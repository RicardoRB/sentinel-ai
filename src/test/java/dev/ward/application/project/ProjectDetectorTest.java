package dev.ward.application.project;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.SPRING_BOOT_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
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
  void detectsGradleGroovyProjectAndWrapper(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    Files.writeString(dir.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
    Files.setPosixFilePermissions(
        dir.resolve("gradlew"), PosixFilePermissions.fromString("rwxr-xr-x"));

    final Project project = detector.detect(dir).orElseThrow();

    assertThat(project.language()).isEqualTo(Language.JAVA);
    assertThat(project.buildTool()).isEqualTo(BuildTool.GRADLE);
    assertThat(project.wrapperAvailable()).isTrue();
    assertThat(project.buildExecutable()).isEqualTo("./gradlew");
  }

  @Test
  void detectsGradleKotlinDslWithoutWrapper(@TempDir final Path dir) throws Exception {
    Files.writeString(
        dir.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") version \"2.0.0\" }\n");

    final Project project = detector.detect(dir).orElseThrow();

    assertThat(project.language()).isEqualTo(Language.KOTLIN);
    assertThat(project.buildTool()).isEqualTo(BuildTool.GRADLE);
    assertThat(project.wrapperAvailable()).isFalse();
    assertThat(project.buildExecutable()).isEqualTo("gradle");
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
    Files.writeString(dir.resolve("README.md"), "not a project marker\n");

    // TempDir lives under the system temp dir, which has no supported marker in any parent.
    final Optional<Project> project = detector.detect(dir);

    assertThat(project).isEmpty();
  }
}
