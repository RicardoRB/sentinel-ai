package dev.ward.infrastructure.project;

import dev.ward.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;

/** Moves a detected project to its aggregator and records its Git repository root. */
final class ProjectRoots {
  private ProjectRoots() {}

  /** Returns the outermost build, up to the Git top-level, that lists the project as a module. */
  static Project aggregatorOf(final Project project) {
    Project result = project;
    Path dir = project.root();
    Path parent = dir.getParent();
    while (parent != null && !Files.exists(dir.resolve(FileSystemProjectInspection.GIT))) {
      if (BuildModules.isModuleOf(parent, result.root())) {
        result = FileSystemProjectInspection.projectAt(parent).orElse(result);
      }
      dir = parent;
      parent = dir.getParent();
    }
    return result;
  }

  /** Sets the nearest enclosing directory with a {@code .git} entry as the repository root. */
  static Project inRepository(final Project project) {
    for (Path dir = project.root(); dir != null; dir = dir.getParent()) {
      if (Files.exists(dir.resolve(FileSystemProjectInspection.GIT))) {
        return project.withRepositoryRoot(dir);
      }
    }
    return project;
  }
}
