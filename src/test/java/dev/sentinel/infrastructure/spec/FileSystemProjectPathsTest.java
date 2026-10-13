package dev.sentinel.infrastructure.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class FileSystemProjectPathsTest {
  private final FileSystemProjectPaths paths = new FileSystemProjectPaths();

  @TempDir Path temp;

  @Test
  void relativePathResolvesAgainstTheRoot() throws IOException {
    final Path root = project();

    assertThat(paths.containedRelative(root, "openspec/changes/foo/proposal.md"))
        .contains(Path.of("openspec/changes/foo/proposal.md"));
  }

  @Test
  void absolutePathInsideTheRootIsAccepted() throws IOException {
    final Path root = project();

    assertThat(
            paths.containedRelative(
                root, root.resolve("openspec/changes/foo/design.md").toString()))
        .contains(Path.of("openspec/changes/foo/design.md"));
  }

  @Test
  void dotSegmentsAreNormalizedAndEscapesRejected() throws IOException {
    final Path root = project();

    assertThat(paths.containedRelative(root, "./openspec/changes/foo/../foo/tasks.md"))
        .contains(Path.of("openspec/changes/foo/tasks.md"));
    assertThat(
            paths.containedRelative(
                root, "openspec/changes/../../../outside/openspec/changes/foo/spec.md"))
        .isEmpty();
    assertThat(paths.containedRelative(root, temp.resolve("elsewhere/a.md").toString())).isEmpty();
    assertThat(paths.containedRelative(root, ".")).isEmpty();
    assertThat(paths.containedRelative(root, "")).isEmpty();
    assertThat(paths.containedRelative(root, null)).isEmpty();
    assertThat(paths.containedRelative(root, "bad\0path")).isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void symlinkLeavingTheProjectIsRejected() throws IOException {
    final Path root = project();
    final Path outside = Files.createDirectories(temp.resolve("outside/changes/foo"));
    Files.createSymbolicLink(root.resolve("openspec/linked"), outside.getParent());

    assertThat(paths.containedRelative(root, "openspec/linked/foo/new.md")).isEmpty();
  }

  @Test
  void existenceChecksDistinguishDirectoriesAndFiles() throws IOException {
    final Path root = project();
    Files.createDirectories(root.resolve("openspec/specs/auth"));
    Files.writeString(root.resolve("openspec/specs/auth/spec.md"), "# auth");

    assertThat(paths.isDirectory(root, Path.of("openspec/changes/foo"))).isTrue();
    assertThat(paths.isDirectory(root, Path.of("openspec/changes/missing"))).isFalse();
    assertThat(paths.isRegularFile(root, Path.of("openspec/specs/auth/spec.md"))).isTrue();
    assertThat(paths.isRegularFile(root, Path.of("openspec/specs/auth"))).isFalse();
  }

  private Path project() throws IOException {
    final Path root = Files.createDirectories(temp.resolve("project"));
    Files.createDirectories(root.resolve("openspec/changes/foo"));
    return root;
  }
}
