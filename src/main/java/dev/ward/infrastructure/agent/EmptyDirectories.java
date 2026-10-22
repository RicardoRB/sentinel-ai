package dev.ward.infrastructure.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Removes directories that removing an integration left empty. */
final class EmptyDirectories {
  private EmptyDirectories() {}

  /** Deletes {@code directory} when it exists and is empty. */
  static void delete(final Path directory) throws IOException {
    if (directory != null && Files.isDirectory(directory)) {
      try (Stream<Path> entries = Files.list(directory)) {
        if (entries.findAny().isEmpty()) {
          Files.deleteIfExists(directory);
        }
      }
    }
  }
}
