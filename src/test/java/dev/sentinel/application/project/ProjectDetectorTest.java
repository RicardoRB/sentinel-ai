package dev.sentinel.application.project;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.SPRING_BOOT_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDetectorTest {

  private final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());

  @Test
  void detectsMavenJavaProject(@TempDir final Path dir) {
    withPom(dir, PLAIN_POM);

    final Project project = detector.detect(dir).orElseThrow();

    assertThat(project.language()).isEqualTo(Language.JAVA);
    assertThat(project.buildTool()).isEqualTo(BuildTool.MAVEN);
    assertThat(project.framework()).isEqualTo(Framework.NONE);
    assertThat(project.root()).isEqualTo(dir.toAbsolutePath().normalize());
  }

  @Test
  void detectsSpringBoot(@TempDir final Path dir) {
    withPom(dir, SPRING_BOOT_POM);

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void detectsSpringBootFromDependencyWithoutParent(@TempDir final Path dir) {
    withPom(
        dir,
        """
                <project><dependencies><dependency>
                  <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId>
                </dependency></dependencies></project>
                """);

    assertThat(detector.detect(dir))
        .get()
        .extracting(Project::framework)
        .isEqualTo(Framework.SPRING_BOOT);
  }

  @Test
  void findsProjectRootFromSubdirectory(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final Path sub = Files.createDirectories(dir.resolve("src/main/java"));

    assertThat(detector.detect(sub))
        .get()
        .extracting(Project::root)
        .isEqualTo(dir.toAbsolutePath().normalize());
  }

  @Test
  void stillMavenWhenPomIsMalformed(@TempDir final Path dir) {
    withPom(dir, "<project><unclosed>");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
  }

  @Test
  void rejectsPomWithDoctype(@TempDir final Path dir) {
    withPom(
        dir,
        "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><project>&x;</project>");

    assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
  }

  @Test
  void unsupportedProjectIsNotDetected(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("package.json"), "{}");
    Files.writeString(dir.resolve("build.gradle"), "");

    // TempDir lives under the system temp dir, which has no pom.xml in any parent.
    final Optional<Project> project = detector.detect(dir);

    assertThat(project).isEmpty();
  }
}
