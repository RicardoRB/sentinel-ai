package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.function.Predicate;

/** Applies and rolls back build-tool declarations needed by selected setup options. */
public interface BuildToolConfiguration {
  /** Adds the build plugins {@code gates} need and returns the change for rollback. */
  PomChange apply(Project project, List<InitGateOption> gates);

  /**
   * Adds the build plugins {@code gates} need, configured for {@code preset}; the default ignores
   * the preset.
   */
  default PomChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    return apply(project, gates);
  }

  /**
   * Adds the build plugins {@code gates} need; plugin version updates are applied only when {@code
   * approval} accepts them. The default applies no updates.
   */
  default PomChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Predicate<? super CompatibilityFinding> approval) {
    return apply(project, gates, preset);
  }

  /**
   * Describes, without writing, how the build file would change for these gates. Version updates
   * that need approval are listed as pending unless {@code approved} is true.
   */
  PlannedFileChange plan(
      Project project, List<InitGateOption> gates, QualityPreset preset, boolean approved);

  /** Restores the build file to its content before {@code change}. */
  void rollback(PomChange change);
}
