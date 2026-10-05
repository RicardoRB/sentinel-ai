package dev.sentinel.application.project;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.SPRING_BOOT_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectDetectorTest {

    private final dev.sentinel.application.project.ProjectDetector detector = new dev.sentinel.application.project.ProjectDetector(new dev.sentinel.infrastructure.project.FileSystemProjectInspection());

    @Test
    void detectsMavenJavaProject(@TempDir Path dir) {
        withPom(dir, PLAIN_POM);

        Project project = detector.detect(dir).orElseThrow();

        assertThat(project.language()).isEqualTo(Language.JAVA);
        assertThat(project.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(project.framework()).isEqualTo(Framework.NONE);
        assertThat(project.root()).isEqualTo(dir.toAbsolutePath().normalize());
    }

    @Test
    void detectsSpringBoot(@TempDir Path dir) {
        withPom(dir, SPRING_BOOT_POM);

        assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    void detectsSpringBootFromDependencyWithoutParent(@TempDir Path dir) {
        withPom(dir, """
                <project><dependencies><dependency>
                  <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId>
                </dependency></dependencies></project>
                """);

        assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.SPRING_BOOT);
    }

    @Test
    void findsProjectRootFromSubdirectory(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Path sub = Files.createDirectories(dir.resolve("src/main/java"));

        assertThat(detector.detect(sub)).get().extracting(Project::root).isEqualTo(dir.toAbsolutePath().normalize());
    }

    @Test
    void stillMavenWhenPomIsMalformed(@TempDir Path dir) {
        withPom(dir, "<project><unclosed>");

        assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
    }

    @Test
    void rejectsPomWithDoctype(@TempDir Path dir) {
        withPom(dir, "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><project>&x;</project>");

        assertThat(detector.detect(dir)).get().extracting(Project::framework).isEqualTo(Framework.NONE);
    }

    @Test
    void unsupportedProjectIsNotDetected(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");
        Files.writeString(dir.resolve("build.gradle"), "");

        // TempDir lives under the system temp dir, which has no pom.xml in any parent.
        Optional<Project> project = detector.detect(dir);

        assertThat(project).isEmpty();
    }
}
