package dev.sentinel.application.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.doctor.DoctorService.Finding;
import dev.sentinel.application.doctor.DoctorService.Status;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DoctorServiceGuidanceTest {
  @TempDir Path project;
  private final Map<String, String> values = new HashMap<>();
  private Set<String> executables = Set.of("openspec");
  private boolean openSpecDirectory = true;

  @BeforeEach
  void createProject() throws IOException {
    Files.writeString(project.resolve("pom.xml"), "<project/>\n");
  }

  @Test
  void noFindingWhenGuidanceIsNotInstalled() {
    assertThat(guidanceFindings()).isEmpty();
  }

  @Test
  void okWhenEveryPrerequisiteIsPresent() {
    values.put("openSpecGuidance.claude-code", "true");
    values.put("openSpecVerifyWorkflow.claude-code", "true");

    assertThat(guidanceFindings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.status()).isEqualTo(Status.OK);
              assertThat(finding.message()).startsWith("claude-code");
            });
  }

  @Test
  void warnsWhenOpenSpecIsNotOnPath() {
    values.put("openSpecGuidance.opencode", "true");
    values.put("openSpecVerifyWorkflow.opencode", "true");
    executables = Set.of();

    assertThat(guidanceFindings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.status()).isEqualTo(Status.WARNING);
              assertThat(finding.message())
                  .contains("opencode", "not on PATH", "a human must install OpenSpec")
                  .doesNotContain("openspec init", "openspec update");
            });
  }

  @Test
  void warnsWhenVerifyWorkflowOrProjectDirectoryIsMissing() {
    values.put("openSpecGuidance.claude-code", "true");
    openSpecDirectory = false;

    assertThat(guidanceFindings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.status()).isEqualTo(Status.WARNING);
              assertThat(finding.message())
                  .contains("`openspec init`", "`openspec config profile`", "`openspec update`");
            });
  }

  private List<Finding> guidanceFindings() {
    final EnvironmentInspection environment =
        new EnvironmentInspection() {
          @Override
          public EnvironmentFacts inspect(final Path projectRoot) {
            return new EnvironmentFacts(executables, values);
          }

          @Override
          public boolean hasOpenSpecDirectory(final Path projectRoot) {
            return openSpecDirectory;
          }
        };
    return new DoctorService(new ProjectDetector(new FileSystemProjectInspection()), environment)
        .diagnose(project).stream().filter(finding -> "guidance".equals(finding.name())).toList();
  }
}
