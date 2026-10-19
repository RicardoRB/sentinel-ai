package dev.sentinel.domain.config;

import java.nio.file.Path;

/** Port used by partial-check planning to distinguish existing from deleted files. */
@FunctionalInterface
public interface PathExistence {
  boolean exists(Path path);
}
