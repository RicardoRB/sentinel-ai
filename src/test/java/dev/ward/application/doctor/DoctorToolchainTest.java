package dev.ward.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.FixedProjectInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Doctor checks the JVM build tool and never reports Maven for another toolchain. */
class DoctorToolchainTest {
  private static final Path ROOT = Path.of("app");

  @Test
  void unsupportedToolchainWarnsWithoutMentioningMaven() {
    final List<Finding> findings =
        diagnose(new Project(ROOT, Language.RUST, BuildTool.CARGO, Framework.NONE));

    assertThat(findings)
        .contains(
            new Finding("cargo", Status.WARNING, "Ward does not check the Cargo toolchain yet."));
    assertThat(findings).extracting(Finding::message).noneMatch(m -> m.contains("Maven"));
  }

  @Test
  void mavenWithoutWrapperOrExecutableIsAnError() {
    final List<Finding> findings =
        diagnose(new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE));

    assertThat(findings).contains(new Finding("maven", Status.ERROR, "System Maven is required."));
  }

  private static List<Finding> diagnose(final Project project) {
    return new DoctorService(
            new ProjectDetector(new FixedProjectInspection(project)),
            new FakeEnvironmentInspection())
        .diagnose(ROOT);
  }
}
