package dev.sentinel.application.init;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
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
  public void rollback(final RuleFileChange change) {
    // Nothing to restore for the compatibility no-op port.
  }
}
