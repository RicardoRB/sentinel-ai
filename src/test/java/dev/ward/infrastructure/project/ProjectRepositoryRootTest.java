package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Detection records the Git top-level that contains the project. */
class ProjectRepositoryRootTest {
  private static final String POM = "pom.xml";
  private static final String LEAF = "<project><artifactId>leaf</artifactId></project>";

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();

  @Test
  void rootProjectIsNotNested() throws IOException {
    Files.createDirectories(root.resolve(".git"));
    Files.writeString(root.resolve(POM), LEAF);

    final Project project = inspection.detect(root).orElseThrow();

    assertThat(project.repositoryRoot()).isEqualTo(project.root());
    assertThat(project.projectPath()).isEmpty();
    assertThat(project.nested()).isFalse();
  }

  @Test
  void nestedProjectReportsItsRepositoryRelativePath() throws IOException {
    Files.createDirectories(root.resolve(".git"));
    final Path api = Files.createDirectories(root.resolve("apps/api"));
    Files.writeString(api.resolve(POM), LEAF);

    final Project project = inspection.detect(api).orElseThrow();

    assertThat(project.repositoryRoot()).isEqualTo(root.toAbsolutePath().normalize());
    assertThat(project.projectPath()).isEqualTo("apps/api");
    assertThat(project.nested()).isTrue();
    assertThat(inspection.discover(root).getFirst().project().projectPath()).isEqualTo("apps/api");
  }

  @Test
  void gitFileMarksAWorktreeOrSubmoduleRoot() throws IOException {
    Files.createDirectories(root.resolve(".git"));
    final Path module = Files.createDirectories(root.resolve("vendor/lib"));
    Files.writeString(module.resolve(".git"), "gitdir: ../../.git/modules/lib\n");
    Files.writeString(module.resolve(POM), LEAF);

    final Project project = inspection.detect(module).orElseThrow();

    assertThat(project.repositoryRoot()).isEqualTo(module.toAbsolutePath().normalize());
    assertThat(project.nested()).isFalse();
  }

  @Test
  void projectOutsideGitUsesItsOwnRoot() throws IOException {
    final Path api = Files.createDirectories(root.resolve("apps/api"));
    Files.writeString(api.resolve(POM), LEAF);

    final Project project = inspection.detect(api).orElseThrow();

    assertThat(project.repositoryRoot()).isEqualTo(project.root());
    assertThat(project.nested()).isFalse();
  }
}
