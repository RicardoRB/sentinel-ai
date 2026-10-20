package dev.sentinel.domain.learning;

import java.util.Objects;

/**
 * A recurring rule finding to show the agent.
 *
 * @param key the gate and rule that recurred
 * @param gate the gate ID
 * @param rule the tool rule that recurred
 * @param occurrences how many separate times it recurred
 * @param summary one example finding message
 */
public record LearningPrompt(
    String key, String gate, String rule, int occurrences, String summary) {
  /**
   * Validates the prompt.
   *
   * @throws NullPointerException if the key, gate, rule, or summary is missing
   */
  public LearningPrompt {
    Objects.requireNonNull(key);
    Objects.requireNonNull(gate);
    Objects.requireNonNull(rule);
    Objects.requireNonNull(summary);
    if (occurrences <= 0) {
      throw new IllegalArgumentException("Prompt occurrences must be positive");
    }
  }

  /** Returns the instruction asking the agent to record how to avoid this problem. */
  public String instruction() {
    return "Sentinel: the `"
        + gate
        + "` rule `"
        + rule
        + "` was violated in "
        + occurrences
        + " separate occurrences and is now fixed. Example: \""
        + summary
        + "\". Add a concise coding rule to AGENTS.md that prevents this problem. Describe the"
        + " code to write or avoid; do not write a rule about running the gate, Sentinel, or any"
        + " command. Do not duplicate an existing rule.";
  }
}
