package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.ProjectFixtures;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Maven api-compat needs a released baseline, so init offers it only for published libraries. */
class InitApiCompatCatalogTest {
  @Test
  void offersMavenApiCompatOnlyForPublishedProjects(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(
        dir,
        ProjectFixtures.PLAIN_POM.replace(
            "</project>",
            "<distributionManagement><repository><id>r</id><url>file:///r</url></repository>"
                + "</distributionManagement></project>"));
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());
    final Project published = detector.detect(dir).orElseThrow();
    final Project application =
        new Project(dir, published.language(), published.buildTool(), published.framework());

    assertThat(published.publishesArtifacts()).isTrue();
    assertThat(catalog.gate(published, "api-compat").command())
        .endsWith("-DskipTests", "package", "japicmp:cmp");
    assertThat(catalog.gates(application))
        .extracting(InitGateOption::id)
        .doesNotContain("api-compat");
    assertThatThrownBy(() -> catalog.gate(application, "api-compat"))
        .hasMessageContaining("<distributionManagement>");
  }
}
