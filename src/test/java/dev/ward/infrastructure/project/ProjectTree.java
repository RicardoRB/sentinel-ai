package dev.ward.infrastructure.project;

import dev.ward.domain.project.DiscoveredProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A temporary Git repository tree for discovery tests. */
final class ProjectTree {
  private final Path root;

  ProjectTree(final Path root) throws IOException {
    this.root = root;
    Files.createDirectories(root.resolve(".git"));
  }

  Path path(final String relative) {
    return root.resolve(relative).toAbsolutePath().normalize();
  }

  void write(final String relative, final String content) throws IOException {
    final Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  static List<Path> roots(final List<DiscoveredProject> projects) {
    return projects.stream().map(discovered -> discovered.project().root()).toList();
  }
}
