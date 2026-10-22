package dev.ward.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Canonical paths let an edit reached through a symbolic link match the project root. */
class FilePathExistenceTest {
  private final FilePathExistence existence = new FilePathExistence();

  @Test
  void existingFileThroughALinkResolvesToItsRealPath(@TempDir final Path directory)
      throws IOException {
    final Path project = Files.createDirectories(directory.resolve("project/src"));
    final Path file = Files.writeString(project.resolve("A.java"), "class A {}");
    final Path link = Files.createSymbolicLink(directory.resolve("link"), project.getParent());

    assertThat(existence.canonical(link.resolve("src/A.java"))).isEqualTo(file.toRealPath());
  }

  @Test
  void deletedFileThroughALinkResolvesItsNearestExistingAncestor(@TempDir final Path directory)
      throws IOException {
    final Path project = Files.createDirectories(directory.resolve("project/src"));
    final Path link = Files.createSymbolicLink(directory.resolve("link"), project.getParent());

    assertThat(existence.canonical(link.resolve("src/gone/B.java")))
        .isEqualTo(project.toRealPath().resolve("gone/B.java"));
  }

  @Test
  void pathWithoutAnExistingAncestorIsOnlyNormalized() {
    final Path relative = Path.of("missing/../still-missing/C.java");

    assertThat(existence.canonical(relative)).isEqualTo(relative.toAbsolutePath().normalize());
  }
}
