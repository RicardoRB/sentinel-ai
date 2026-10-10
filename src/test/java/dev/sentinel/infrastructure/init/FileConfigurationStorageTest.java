package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileConfigurationStorageTest {
  private final FileConfigurationStorage storage = new FileConfigurationStorage();

  @Test
  void createsWithoutOverwritingAndRestoresOriginalContent(@TempDir final Path root)
      throws Exception {
    final Path file = root.resolve("sentinel.toml");
    assertThat(storage.read(file)).isEmpty();
    assertThat(storage.create(file, "created\n")).isTrue();
    assertThat(storage.create(file, "must not replace\n")).isFalse();
    assertThat(Files.readString(file)).isEqualTo("created\n");

    storage.replace(file, "replacement\n");
    assertThat(Files.readString(file)).isEqualTo("replacement\n");
    storage.restore(file, "original\n");
    assertThat(storage.read(file)).contains("original\n");
  }
}
