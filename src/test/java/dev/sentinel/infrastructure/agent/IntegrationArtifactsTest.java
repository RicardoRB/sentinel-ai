package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationArtifactsTest {
  @Test
  void protectsOwnershipAcrossPreflightInstallAndRemoval(final @TempDir Path root)
      throws IOException {
    final Path target = root.resolve("generated");
    assertThat(IntegrationArtifacts.preflight(target, "marker")).isNull();
    assertThat(IntegrationArtifacts.install(target, "marker", "marker\n", "created").status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(IntegrationArtifacts.install(target, "marker", "new", "created").status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(IntegrationArtifacts.remove(target, "marker", "removed").status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(IntegrationArtifacts.remove(target, "marker", "missing").status())
        .isEqualTo(IntegrationResult.Status.NOT_FOUND);

    Files.writeString(target, "user-owned");
    assertThat(IntegrationArtifacts.preflight(target, "marker").status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(IntegrationArtifacts.remove(target, "marker", "removed").status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(target).hasContent("user-owned");
  }
}
