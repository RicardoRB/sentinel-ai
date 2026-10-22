package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TomlConfigurationFixtures {
  private TomlConfigurationFixtures() {}

  static WardConfiguration read(
      final TomlConfigurationReader reader, final Path directory, final String toml)
      throws IOException {
    final Path file = directory.resolve("ward.toml");
    Files.writeString(file, toml);
    return reader.read(file);
  }
}
