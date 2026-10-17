package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.project.Project;

/** Generates and rolls back an optional architecture test. */
public interface ArchitectureTestGeneration {
  ArchitectureTestChange apply(Project project, String style);

  default ArchitectureTestChange apply(
      final Project project, final String style, final QualityPreset.ArchitectureExtras extras) {
    return apply(project, style);
  }

  /** Describes, without writing, whether {@link #apply} would create the test. */
  PlannedFileChange plan(Project project, String style, QualityPreset.ArchitectureExtras extras);

  void rollback(ArchitectureTestChange change);
}
