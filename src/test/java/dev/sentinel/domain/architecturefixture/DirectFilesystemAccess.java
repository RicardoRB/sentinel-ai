package dev.sentinel.domain.architecturefixture;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Architecture fixture that the boundary rules must reject: it touches the file system directly.
 */
public final class DirectFilesystemAccess {
  /** Checks {@code path} directly, which the domain must not do. */
  public boolean exists(final Path path) {
    return Files.exists(path);
  }
}
