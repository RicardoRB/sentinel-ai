package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeIntegrationTransactionTest {
  @TempDir Path root;

  @Test
  void rollbackRestoresChangedAndRemovesCreatedFiles() throws Exception {
    final Path existing = root.resolve("hooks/config.json");
    final Path created = root.resolve("hooks/new/hook.sh");
    Files.createDirectories(existing.getParent());
    Files.writeString(existing, "original");
    final NativeIntegrationTransaction transaction =
        NativeIntegrationTransaction.capture(existing, created);
    Files.writeString(existing, "changed");
    Files.createDirectories(created.getParent());
    Files.writeString(created, "new");
    Files.delete(existing);

    transaction.rollback();

    assertThat(existing).hasContent("original");
    assertThat(created).doesNotExist();
  }

  @Test
  void rollbackHandlesPathsWithoutParentDirectory() throws Exception {
    final Path relative = Path.of("sentinel-rollback-" + System.nanoTime() + ".tmp");
    try {
      Files.writeString(relative, "original");
      final NativeIntegrationTransaction transaction =
          NativeIntegrationTransaction.capture(relative);
      Files.writeString(relative, "changed");

      transaction.rollback();

      assertThat(relative).hasContent("original");
    } finally {
      Files.deleteIfExists(relative);
    }
  }
}
