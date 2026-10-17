package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Applies and rolls back build-tool declarations needed by selected setup options. */
public interface BuildToolConfiguration {
  PomChange apply(Project project, List<InitGateOption> gates);

  default PomChange apply(
      final Project project, final List<InitGateOption> gates, final QualityPreset preset) {
    return apply(project, gates);
  }

  default PomChange apply(
      final Project project,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final Object approval) {
    return apply(project, gates, preset);
  }

  /**
   * Describes, without writing, how the build file would change for these gates. Version updates
   * that need approval are listed as pending unless {@code approved} is true.
   */
  PlannedFileChange plan(
      Project project, List<InitGateOption> gates, QualityPreset preset, boolean approved);

  void rollback(PomChange change);
}
