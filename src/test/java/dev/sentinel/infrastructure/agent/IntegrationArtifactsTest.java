package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationArtifactsTest {
  private static final String MARKER = "marker";

  @Test
  void protectsOwnershipAcrossPreflightInstallAndRemoval(@TempDir final Path root)
      throws IOException {
    final Path target = root.resolve("generated");
    assertThat(IntegrationArtifacts.preflight(target, MARKER)).isNull();
    assertThat(IntegrationArtifacts.install(target, MARKER, "marker\n", "created").status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(IntegrationArtifacts.install(target, MARKER, "new", "created").status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(IntegrationArtifacts.remove(target, MARKER, "removed").status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(IntegrationArtifacts.remove(target, MARKER, "missing").status())
        .isEqualTo(IntegrationResult.Status.NOT_FOUND);

    Files.writeString(target, "user-owned");
    assertThat(IntegrationArtifacts.preflight(target, MARKER).status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(IntegrationArtifacts.remove(target, MARKER, "removed").status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(target).hasContent("user-owned");
  }

  @Test
  void installsAndRefreshesEveryTargetIdempotently(@TempDir final Path root) throws IOException {
    final IntegrationArtifacts.Target first =
        new IntegrationArtifacts.Target(root.resolve("a/first"), MARKER, "marker v1\n");
    final IntegrationArtifacts.Target second =
        new IntegrationArtifacts.Target(root.resolve("b/c/second"), MARKER, "marker v1\n");

    final IntegrationResult created =
        IntegrationArtifacts.installOrRefreshAll(List.of(first, second), "present", "changed");
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(created.changed())
        .containsExactly(first.path().toString(), second.path().toString());
    assertThat(
            IntegrationArtifacts.installOrRefreshAll(List.of(first, second), "present", "changed")
                .status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);

    final IntegrationArtifacts.Target refreshed =
        new IntegrationArtifacts.Target(second.path(), MARKER, "marker v2\n");
    final IntegrationResult upgraded =
        IntegrationArtifacts.installOrRefreshAll(List.of(first, refreshed), "present", "changed");
    assertThat(upgraded.changed()).containsExactly(second.path().toString());
    assertThat(second.path()).hasContent("marker v2\n");
  }

  @Test
  void conflictPreflightLeavesEveryTargetUntouched(@TempDir final Path root) throws IOException {
    final IntegrationArtifacts.Target owned =
        new IntegrationArtifacts.Target(root.resolve("owned"), MARKER, "marker\n");
    final IntegrationArtifacts.Target user =
        new IntegrationArtifacts.Target(root.resolve("user"), MARKER, "marker\n");
    Files.writeString(user.path(), "user-owned");

    final IntegrationResult install =
        IntegrationArtifacts.installOrRefreshAll(List.of(owned, user), "present", "changed");
    assertThat(install.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(install.changed()).containsExactly(user.path().toString());
    assertThat(owned.path()).doesNotExist();

    Files.writeString(owned.path(), "marker\n");
    final IntegrationResult removal =
        IntegrationArtifacts.removeAll(List.of(owned, user), root, "missing", "removed");
    assertThat(removal.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(owned.path()).hasContent("marker\n");
    assertThat(user.path()).hasContent("user-owned");
  }

  @Test
  void removesOwnedTargetsAndEmptyDirectoriesWithinBoundary(@TempDir final Path root)
      throws IOException {
    final Path boundary = root.resolve(".agent");
    final IntegrationArtifacts.Target nested =
        new IntegrationArtifacts.Target(boundary.resolve("skills/name/SKILL.md"), "m", "m\n");
    final IntegrationArtifacts.Target kept =
        new IntegrationArtifacts.Target(boundary.resolve("hooks/hook"), "m", "m\n");
    IntegrationArtifacts.installOrRefreshAll(List.of(nested, kept), "present", "changed");
    Files.writeString(boundary.resolve("hooks/unrelated"), "user");

    final IntegrationResult removed =
        IntegrationArtifacts.removeAll(List.of(nested, kept), boundary, "missing", "removed");

    assertThat(removed.status()).isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(removed.changed()).containsExactly(nested.path().toString(), kept.path().toString());
    assertThat(boundary.resolve("skills")).doesNotExist();
    assertThat(boundary.resolve("hooks/unrelated")).hasContent("user");
    assertThat(boundary).isDirectory();
    assertThat(
            IntegrationArtifacts.removeAll(List.of(nested, kept), boundary, "missing", "removed")
                .status())
        .isEqualTo(IntegrationResult.Status.NOT_FOUND);
  }
}
