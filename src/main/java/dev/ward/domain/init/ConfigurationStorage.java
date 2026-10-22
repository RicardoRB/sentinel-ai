package dev.ward.domain.init;

import java.nio.file.Path;
import java.util.Optional;

/** Narrow persistence contract for the generated Ward configuration. */
public interface ConfigurationStorage {
  /** Returns whether {@code file} exists. */
  boolean exists(Path file);

  /** Returns the content of {@code file}, or empty when it does not exist. */
  Optional<String> read(Path file);

  /**
   * Creates {@code file} with {@code content}; returns false, writing nothing, when it already
   * exists.
   */
  boolean create(Path file, String content);

  /** Overwrites the existing {@code file} with {@code content}. */
  void replace(Path file, String content);

  /** Writes {@code originalContent} back to {@code file} after a failed setup. */
  void restore(Path file, String originalContent);
}
