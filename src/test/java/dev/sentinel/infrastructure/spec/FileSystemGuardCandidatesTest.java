package dev.sentinel.infrastructure.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.spec.ApprovedArtifactPolicy;
import dev.sentinel.domain.spec.GuardOperation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemGuardCandidatesTest {
  private static final String SPEC = "openspec/specs/x/spec.md";
  private static final List<Path> ROOTS = ApprovedArtifactPolicy.PROTECTED_ROOTS;
  private final FileSystemProjectPaths paths = new FileSystemProjectPaths();

  @TempDir Path temp;

  private Set<Path> candidates(final Path root, final String path) {
    return paths.guardCandidates(root, "", path, ROOTS);
  }

  private boolean protectedFor(final Path root, final String path, final GuardOperation operation) {
    return candidates(root, path).stream()
        .anyMatch(candidate -> ApprovedArtifactPolicy.evaluate(candidate, operation).isPresent());
  }

  @Test
  void lexicalTraversalIsNormalized() throws IOException {
    final Path root = project();

    assertThat(candidates(root, "openspec/changes/foo/../../specs/x/spec.md"))
        .contains(Path.of(SPEC));
    assertThat(protectedFor(root, root + "/openspec/specs/x/spec.md", GuardOperation.WRITE))
        .isTrue();
  }

  @Test
  void workingDirectoryAnchorsRelativePaths() throws IOException {
    final Path root = project();

    assertThat(paths.guardCandidates(root, "openspec", "specs/x/spec.md", ROOTS))
        .contains(Path.of(SPEC));
    assertThat(
            paths.guardCandidates(root, root.resolve("openspec/specs").toString(), "x.md", ROOTS))
        .contains(Path.of("openspec/specs/x.md"));
  }

  @Test
  void newFileInANewActiveChangeIsNotProtected() throws IOException {
    final Path root = project();

    assertThat(protectedFor(root, "openspec/changes/bar/proposal.md", GuardOperation.WRITE))
        .isFalse();
    assertThat(protectedFor(root, "openspec/changes/foo", GuardOperation.DELETE)).isFalse();
  }

  @Test
  void deletingAnAncestorOrTheProjectReachesTheProtectedRoot() throws IOException {
    final Path root = project();

    assertThat(protectedFor(root, "openspec", GuardOperation.DELETE)).isTrue();
    assertThat(protectedFor(root, ".", GuardOperation.DELETE)).isTrue();
    assertThat(protectedFor(root, "..", GuardOperation.DELETE)).isTrue();
    assertThat(candidates(root, "..")).contains(Path.of(""));
  }

  @Test
  void unrelatedOrUnparsablePathsHaveNoProtectedCandidate() throws IOException {
    final Path root = project();

    assertThat(candidates(root, temp.resolve("elsewhere/a.md").toString())).isEmpty();
    assertThat(candidates(root, "")).isEmpty();
    assertThat(candidates(root, null)).isEmpty();
    assertThat(candidates(root, "bad\0path")).isEmpty();
    assertThat(protectedFor(root, "src/App.java", GuardOperation.WRITE)).isFalse();
  }

  @Test
  void projectWithoutProtectedDirectoriesStillReportsLexicalPaths() throws IOException {
    final Path root = Files.createDirectories(temp.resolve("bare"));

    assertThat(paths.guardCandidates(root, null, SPEC, ROOTS)).containsExactly(Path.of(SPEC));
  }

  @Test
  void activeChangesExcludeArchiveHiddenAndFiles() throws IOException {
    final Path root = project();
    Files.createDirectories(root.resolve("openspec/changes/bar"));
    Files.createDirectories(root.resolve("openspec/changes/.cache"));
    Files.writeString(root.resolve("openspec/changes/README.md"), "");

    assertThat(paths.activeChanges(root)).containsExactly("bar", "foo");
    assertThat(paths.activeChanges(temp.resolve("missing"))).isEmpty();
  }

  private Path project() throws IOException {
    final Path root = Files.createDirectories(temp.resolve("project"));
    Files.createDirectories(root.resolve("openspec/changes/foo"));
    Files.createDirectories(root.resolve("openspec/changes/archive/2026-10-05-old"));
    Files.createDirectories(root.resolve("openspec/specs/x"));
    Files.writeString(root.resolve(SPEC), "# x");
    return root;
  }
}
