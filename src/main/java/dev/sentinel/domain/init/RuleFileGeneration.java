package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Writes and rolls back preset-owned rule files. */
public interface RuleFileGeneration {
  /** Writes the rule files {@code preset} needs for {@code gates}, keeping user-owned files. */
  RuleFileChange apply(Project project, QualityPreset preset, List<InitGateOption> gates);

  /** Describes, without writing, what {@link #apply} would do with each rule file. */
  List<PlannedFileChange> plan(Project project, QualityPreset preset, List<InitGateOption> gates);

  /** Restores rule files to their state before {@code change}. */
  void rollback(RuleFileChange change);
}
