package dev.sentinel.infrastructure.spec;

import dev.sentinel.domain.spec.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;

/** Filesystem-backed path containment that follows symbolic links of existing ancestors. */
public final class FileSystemProjectPaths implements ProjectPaths {

  @Inject
  public FileSystemProjectPaths() {
    // Stateless adapter; Dagger constructs it through this constructor.
  }

  @Override
  public Optional<Path> containedRelative(final Path projectRoot, final String path) {
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    try {
      final Path root = projectRoot.toAbsolutePath().normalize().toRealPath();
      final Path requested = Path.of(path);
      final Path absolute =
          (requested.isAbsolute() ? requested : projectRoot.toAbsolutePath().resolve(requested))
              .normalize();
      final Path real = realPath(absolute);
      return real.startsWith(root) && !real.equals(root)
          ? Optional.of(root.relativize(real))
          : Optional.empty();
    } catch (InvalidPathException | IOException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean isDirectory(final Path projectRoot, final Path relative) {
    return Files.isDirectory(projectRoot.resolve(relative));
  }

  @Override
  public boolean isRegularFile(final Path projectRoot, final Path relative) {
    return Files.isRegularFile(projectRoot.resolve(relative));
  }

  /** Resolves links in the deepest existing ancestor and keeps the not-yet-existing remainder. */
  private static Path realPath(final Path absolute) throws IOException {
    Path existing = absolute;
    while (existing != null && !Files.exists(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      return absolute;
    }
    return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
  }
}
