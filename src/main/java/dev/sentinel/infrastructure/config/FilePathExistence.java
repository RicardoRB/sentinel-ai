package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.PathExistence;
import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem adapter used when planning partial checks. */
public final class FilePathExistence implements PathExistence {
  @Override
  public boolean exists(final Path path) {
    return Files.exists(path);
  }
}
