package dev.sentinel.domain.learning;

import java.util.Objects;

public record LearningPrompt(String key, String gate, int occurrences, String summary) {
  public LearningPrompt {
    Objects.requireNonNull(key);
    Objects.requireNonNull(gate);
    Objects.requireNonNull(summary);
    if (occurrences < 1) {
      throw new IllegalArgumentException("Prompt occurrences must be positive");
    }
  }

  public String instruction() {
    return "Sentinel: the `"
        + gate
        + "` gate failed in "
        + occurrences
        + " separate occurrences and is now passing. Add a concise preventive instruction to AGENTS.md describing what you changed to fix it, so the error does not recur. Do not duplicate an existing rule.";
  }
}
