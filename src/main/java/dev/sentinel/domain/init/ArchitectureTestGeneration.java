package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Generates and rolls back an optional architecture test. */
public interface ArchitectureTestGeneration {
  /** Returns simple names of existing ArchUnit test classes in the project's test sources. */
  List<String> existingArchUnitTests(Project project);

  /**
   * Writes the ArchUnit test for {@code style}, or reuses an existing one; the change can be rolled
   * back.
   */
  ArchitectureTestChange apply(Project project, String style);

  /**
   * Writes the ArchUnit test with the preset's optional {@code extras}; the default ignores them.
   */
  default ArchitectureTestChange apply(
      final Project project, final String style, final QualityPreset.ArchitectureExtras extras) {
    return apply(project, style);
  }

  /** Describes, without writing, whether {@link #apply} would create the test. */
  PlannedFileChange plan(Project project, String style, QualityPreset.ArchitectureExtras extras);

  /** Removes a test that {@code change} created and leaves reused tests untouched. */
  void rollback(ArchitectureTestChange change);
}
