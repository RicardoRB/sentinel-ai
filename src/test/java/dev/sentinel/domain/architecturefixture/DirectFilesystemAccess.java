package dev.sentinel.domain.architecturefixture;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DirectFilesystemAccess {
  public boolean exists(final Path path) {
    return Files.exists(path);
  }
}
