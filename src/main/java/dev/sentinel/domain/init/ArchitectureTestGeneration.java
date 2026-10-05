package dev.sentinel.domain.init;

import dev.sentinel.domain.project.Project;

/** Generates and rolls back an optional architecture test. */
public interface ArchitectureTestGeneration {
  ArchitectureTestChange apply(Project project, String style);

  void rollback(ArchitectureTestChange change);
}
