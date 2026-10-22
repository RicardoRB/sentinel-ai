package dev.ward.infrastructure.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.ward.domain.spec.ApprovedArtifactPolicy;
import dev.ward.domain.spec.GuardOperation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Aliases that reach protected OpenSpec directories through links or case variants. */
class FileSystemGuardAliasTest {
  private static final String SPEC = "openspec/specs/x/spec.md";
  private final FileSystemProjectPaths paths = new FileSystemProjectPaths();

  @TempDir Path temp;

  private Set<Path> candidates(final Path root, final String path) {
    return paths.guardCandidates(root, "", path, ApprovedArtifactPolicy.PROTECTED_ROOTS);
  }

  private boolean protectedFor(final Path root, final String path, final GuardOperation operation) {
    return candidates(root, path).stream()
        .anyMatch(candidate -> ApprovedArtifactPolicy.evaluate(candidate, operation).isPresent());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void symlinkAliasInsideTheProjectIsDetected() throws IOException {
    final Path root = project();
    Files.createDirectories(root.resolve("docs"));
    Files.createSymbolicLink(root.resolve("docs/approved"), root.resolve("openspec/specs"));

    assertThat(candidates(root, "docs/approved/x/spec.md")).contains(Path.of(SPEC));
    assertThat(protectedFor(root, "docs/approved/new/spec.md", GuardOperation.WRITE)).isTrue();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void protectedDirectoryLinkedOutsideTheProjectIsStillProtected() throws IOException {
    final Path root = Files.createDirectories(temp.resolve("linked-project"));
    Files.createDirectories(root.resolve("openspec/changes/foo"));
    final Path outside = Files.createDirectories(temp.resolve("shared-specs/x"));
    Files.writeString(outside.resolve("spec.md"), "# x");
    Files.createSymbolicLink(root.resolve("openspec/specs"), outside.getParent());

    assertThat(protectedFor(root, SPEC, GuardOperation.WRITE)).isTrue();
    assertThat(candidates(root, outside.resolve("spec.md").toString())).contains(Path.of(SPEC));
  }

  @Test
  void caseVariantIsProtectedOnCaseInsensitiveFileSystems() throws IOException {
    final Path root = project();
    Files.writeString(root.resolve("probe"), "");
    assumeTrue(Files.exists(root.resolve("PROBE")), "file system is case-sensitive");

    assertThat(protectedFor(root, "OpenSpec/Specs/x/spec.md", GuardOperation.WRITE)).isTrue();
    assertThat(protectedFor(root, "OPENSPEC", GuardOperation.DELETE)).isTrue();
  }

  private Path project() throws IOException {
    final Path root = Files.createDirectories(temp.resolve("project"));
    Files.createDirectories(root.resolve("openspec/changes/foo"));
    Files.createDirectories(root.resolve("openspec/specs/x"));
    Files.writeString(root.resolve(SPEC), "# x");
    return root;
  }
}
