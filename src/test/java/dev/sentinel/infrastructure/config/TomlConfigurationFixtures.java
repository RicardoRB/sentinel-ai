package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.SentinelConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TomlConfigurationFixtures {
  private TomlConfigurationFixtures() {}

  static SentinelConfiguration read(
      final TomlConfigurationReader reader, final Path directory, final String toml)
      throws IOException {
    final Path file = directory.resolve("sentinel.toml");
    Files.writeString(file, toml);
    return reader.read(file);
  }
}
