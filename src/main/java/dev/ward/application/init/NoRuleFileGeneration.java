package dev.ward.application.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.init.RuleFileGeneration;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Compatibility rule-file port that writes nothing. */
final class NoRuleFileGeneration implements RuleFileGeneration {
  @Override
  public RuleFileChange apply(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    return new RuleFileChange(project.root().resolve("config"), false, Map.of(), List.of());
  }

  @Override
  public List<PlannedFileChange> plan(
      final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
    return List.of();
  }

  @Override
  public void rollback(final RuleFileChange change) {
    // Nothing to restore for the compatibility no-op port.
  }
}
