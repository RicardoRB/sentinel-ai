package dev.sentinel.domain.learning;

import java.util.Objects;

/**
 * A repeated failure to show the agent.
 *
 * @param key the gate and status that repeated
 * @param gate the gate ID
 * @param occurrences how many consecutive times it failed
 * @param summary a short failure summary
 */
public record LearningPrompt(String key, String gate, int occurrences, String summary) {
  /**
   * Validates the prompt.
   *
   * @throws NullPointerException if the key, gate, or summary is missing
   */
  public LearningPrompt {
    Objects.requireNonNull(key);
    Objects.requireNonNull(gate);
    Objects.requireNonNull(summary);
    if (occurrences <= 0) {
      throw new IllegalArgumentException("Prompt occurrences must be positive");
    }
  }

  /** Returns the instruction asking the agent to record how to avoid this failure. */
  public String instruction() {
    return "Sentinel: the `"
        + gate
        + "` gate failed in "
        + occurrences
        + " separate occurrences and is now passing. Add a concise preventive instruction to AGENTS.md describing what you changed to fix it, so the error does not recur. Do not duplicate an existing rule.";
  }
}
