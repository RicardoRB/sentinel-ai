package dev.ward.domain.config;

import java.nio.file.Path;

/** Port used by partial-check planning to distinguish existing from deleted files. */
@FunctionalInterface
public interface PathExistence {
  /** Returns whether {@code path} exists. */
  boolean exists(Path path);

  /**
   * Returns the absolute form of {@code path} used to compare it with the project root. Adapters
   * resolve symbolic links so an edited path reached through a link still lies inside the root.
   */
  default Path canonical(final Path path) {
    return path.toAbsolutePath().normalize();
  }
}
