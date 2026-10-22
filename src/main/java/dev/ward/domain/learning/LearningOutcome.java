package dev.ward.domain.learning;

import java.util.List;
import java.util.Objects;

/**
 * The result of applying a check report to the ledger.
 *
 * @param ledger the updated ledger
 * @param prompts repeated failures to show the agent
 * @param warnings problems reading or writing the store
 */
public record LearningOutcome(
    LearningLedger ledger, List<LearningPrompt> prompts, List<String> warnings) {
  /**
   * Copies the prompts and warnings.
   *
   * @throws NullPointerException if {@code ledger} is missing
   */
  public LearningOutcome {
    Objects.requireNonNull(ledger);
    prompts = List.copyOf(prompts);
    warnings = List.copyOf(warnings);
  }

  /** Creates an outcome without prompts, carrying {@code warning}. */
  public static LearningOutcome withoutPrompts(final LearningLedger ledger, final String warning) {
    return new LearningOutcome(ledger, List.of(), List.of(warning));
  }
}
