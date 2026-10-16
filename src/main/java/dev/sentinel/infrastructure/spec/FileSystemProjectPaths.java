package dev.sentinel.infrastructure.spec;

import dev.sentinel.domain.spec.ProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Filesystem-backed path containment that follows symbolic links of existing ancestors. */
public final class FileSystemProjectPaths implements ProjectPaths {
  private static final Path CHANGES = Path.of("openspec", "changes");
  private static final String ARCHIVE = "archive";
  private static final String HIDDEN = ".";

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

  @Override
  public Set<Path> guardCandidates(
      final Path projectRoot,
      final String workingDirectory,
      final String path,
      final List<Path> protectedRoots) {
    if (path == null || path.isBlank()) {
      return Set.of();
    }
    try {
      final Path root = projectRoot.toAbsolutePath().normalize();
      final Path base =
          workingDirectory == null || workingDirectory.isBlank()
              ? root
              : root.resolve(workingDirectory).normalize();
      final Path absolute = base.resolve(path).normalize();
      final Path real = realPath(absolute);
      final Path realRoot = root.toRealPath();
      final Set<Path> candidates = new LinkedHashSet<>();
      if (absolute.startsWith(root)) {
        candidates.add(root.relativize(absolute));
      }
      if (real.startsWith(realRoot)) {
        candidates.add(realRoot.relativize(real));
      }
      for (final Path protectedRoot : protectedRoots) {
        candidates.addAll(new Alias(root, absolute, real).of(protectedRoot));
      }
      return candidates;
    } catch (InvalidPathException | IOException e) {
      return Set.of();
    }
  }

  @Override
  public List<String> activeChanges(final Path projectRoot) {
    final Path changes = projectRoot.resolve(CHANGES);
    if (!Files.isDirectory(changes)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(changes)) {
      return entries
          .filter(Files::isDirectory)
          .map(Path::getFileName)
          .filter(name -> name != null)
          .map(Path::toString)
          .filter(name -> !ARCHIVE.equals(name) && !name.startsWith(HIDDEN))
          .sorted()
          .toList();
    } catch (IOException e) {
      return List.of();
    }
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

  /** Finds the project-relative forms in which one path reaches one protected root. */
  private record Alias(Path root, Path absolute, Path real) {

    Set<Path> of(final Path protectedRoot) throws IOException {
      final Path location = root.resolve(protectedRoot);
      final Set<Path> aliases = new LinkedHashSet<>();
      if (!Files.exists(location)) {
        return aliases;
      }
      final Path realLocation = location.toRealPath();
      if (real.startsWith(realLocation)) {
        aliases.add(protectedRoot.resolve(realLocation.relativize(real).toString()));
      } else if (realLocation.startsWith(real)) {
        aliases.add(ancestor(protectedRoot, realLocation.getNameCount() - real.getNameCount()));
      }
      inside(location, protectedRoot).ifPresent(aliases::add);
      containing(location).ifPresent(aliases::add);
      return aliases;
    }

    /** An existing ancestor of the path that is the same file as the protected root. */
    private Optional<Path> inside(final Path location, final Path protectedRoot)
        throws IOException {
      for (Path current = absolute; current != null; current = current.getParent()) {
        if (Files.exists(current) && Files.isSameFile(current, location)) {
          return Optional.of(protectedRoot.resolve(current.relativize(absolute).toString()));
        }
      }
      return Optional.empty();
    }

    /** An ancestor of the protected root, within the project, that is the same file as the path. */
    private Optional<Path> containing(final Path location) throws IOException {
      if (!Files.exists(absolute)) {
        return Optional.empty();
      }
      Path current = location;
      while (current != null && current.startsWith(root)) {
        if (Files.isSameFile(current, absolute)) {
          return Optional.of(root.relativize(current));
        }
        current = current.getParent();
      }
      return Optional.empty();
    }

    private static Path ancestor(final Path protectedRoot, final int levels) {
      final int remaining = protectedRoot.getNameCount() - levels;
      return remaining <= 0 ? Path.of("") : protectedRoot.subpath(0, remaining);
    }
  }
}
