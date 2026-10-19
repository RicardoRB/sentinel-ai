package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.PathExistence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem adapter used when planning partial checks. */
public final class FilePathExistence implements PathExistence {
  @Override
  public boolean exists(final Path path) {
    return Files.exists(path);
  }

  /**
   * Resolves symbolic links in the nearest existing ancestor, so deleted files are canonicalized
   * too; the path is returned only normalized when no ancestor can be resolved.
   */
  @Override
  public Path canonical(final Path path) {
    final Path absolute = path.toAbsolutePath().normalize();
    Path existing = absolute;
    while (existing != null && !Files.exists(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      return absolute;
    }
    try {
      return existing.toRealPath().resolve(existing.relativize(absolute));
    } catch (IOException e) {
      return absolute;
    }
  }
}
