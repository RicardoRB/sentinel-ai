package dev.sentinel.domain.learning;

import java.util.List;
import java.util.Objects;

public record LearningOutcome(
    LearningLedger ledger, List<LearningPrompt> prompts, List<String> warnings) {
  public LearningOutcome {
    Objects.requireNonNull(ledger);
    prompts = List.copyOf(prompts);
    warnings = List.copyOf(warnings);
  }

  public static LearningOutcome withoutPrompts(LearningLedger ledger, String warning) {
    return new LearningOutcome(ledger, List.of(), List.of(warning));
  }
}
