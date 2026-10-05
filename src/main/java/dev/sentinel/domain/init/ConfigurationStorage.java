package dev.sentinel.domain.init;

import java.nio.file.Path;
import java.util.Optional;

/** Narrow persistence contract for the generated Sentinel configuration. */
public interface ConfigurationStorage {
  boolean exists(Path file);

  Optional<String> read(Path file);

  boolean create(Path file, String content);

  void replace(Path file, String content);

  void restore(Path file, String originalContent);
}
