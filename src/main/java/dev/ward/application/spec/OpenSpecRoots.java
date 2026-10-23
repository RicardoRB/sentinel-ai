package dev.ward.application.spec;

import dev.ward.domain.project.Project;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

/** Finds the directory that holds a project's {@code openspec/}, which may sit above it. */
final class OpenSpecRoots {
  private OpenSpecRoots() {}

  /**
   * Returns the directory an OpenSpec gate runs from: the project root, or {@code configured}
   * resolved against it. A configured root outside the repository is empty, as the gate refuses it.
   */
  static Optional<Path> configured(final Project project, final String configured) {
    final Path root = project.root().toAbsolutePath().normalize();
    if (configured == null) {
      return Optional.of(root);
    }
    final Path resolved = root.resolve(configured).normalize();
    return resolved.startsWith(project.repositoryRoot().toAbsolutePath().normalize())
        ? Optional.of(resolved)
        : Optional.empty();
  }

  /**
   * Returns {@code path} unchanged when the OpenSpec root is the project root, and otherwise
   * resolved against the project root, so relative agent paths keep meaning the project's files.
   * Segments and links are left for containment to resolve; an unparsable path is empty.
   */
  static Optional<String> againstProject(
      final Project project, final Path openSpecRoot, final String path) {
    final Path root = project.root().toAbsolutePath().normalize();
    if (root.equals(openSpecRoot)) {
      return Optional.of(path);
    }
    try {
      return Optional.of(root.resolve(path).toString());
    } catch (InvalidPathException unparsable) {
      return Optional.empty();
    }
  }
}
