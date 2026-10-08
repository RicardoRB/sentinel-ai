package dev.sentinel.domain.init;

import dev.sentinel.domain.project.Project;
import java.util.List;

/** Writes and rolls back preset-owned rule files. */
public interface RuleFileGeneration {
  RuleFileChange apply(Project project, QualityPreset preset, List<InitGateOption> gates);

  void rollback(RuleFileChange change);
}
