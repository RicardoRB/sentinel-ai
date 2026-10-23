package dev.ward.domain.gate;

import java.nio.file.Path;

/** Outbound port for gates that inspect files in the project working tree. */
public interface ProjectFiles {
  /** Returns whether the regular file {@code path}, relative to {@code root}, exists. */
  boolean exists(Path root, String path);

  /**
   * Returns whether the file {@code path}, relative to {@code root}, contains {@code text}; false
   * when the file cannot be read.
   */
  boolean contains(Path root, String path, String text);
}
