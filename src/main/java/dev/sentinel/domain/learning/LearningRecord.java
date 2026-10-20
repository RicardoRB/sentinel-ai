package dev.sentinel.domain.learning;

import java.time.Instant;
import java.util.Objects;

/**
 * One tracked failure.
 *
 * @param key the gate and status
 * @param gate the gate ID
 * @param status the failing status
 * @param summary a short failure summary
 * @param occurrences how many consecutive times it failed
 * @param state whether it is still failing
 * @param firstSeen when it first failed
 * @param lastSeen when it was last seen
 * @param prompted whether the agent was already prompted
 */
public record LearningRecord(
    String key,
    String gate,
    String status,
    String summary,
    int occurrences,
    LearningState state,
    Instant firstSeen,
    Instant lastSeen,
    boolean prompted) {
  public static final int MAX_SUMMARY_LENGTH = 200;

  /**
   * Validates the record.
   *
   * @throws IllegalArgumentException if an identity field is blank, the summary is too long, the
   *     count is not positive, or the timestamps are out of order
   */
  public LearningRecord {
    Objects.requireNonNull(key);
    Objects.requireNonNull(gate);
    Objects.requireNonNull(status);
    Objects.requireNonNull(summary);
    Objects.requireNonNull(state);
    Objects.requireNonNull(firstSeen);
    Objects.requireNonNull(lastSeen);
    if (key.isBlank() || gate.isBlank() || status.isBlank()) {
      throw new IllegalArgumentException("Learning record identity must not be blank");
    }
    if (summary.length() > MAX_SUMMARY_LENGTH) {
      throw new IllegalArgumentException("Learning summary must be at most 200 characters");
    }
    if (occurrences <= 0) {
      throw new IllegalArgumentException("Learning occurrences must be positive");
    }
    if (lastSeen.isBefore(firstSeen)) {
      throw new IllegalArgumentException("Learning timestamps are out of order");
    }
  }

  /** Returns a copy in {@code nextState}, last seen at {@code seen}. */
  public LearningRecord withState(
      final LearningState nextState, final Instant seen, final boolean nextPrompted) {
    return new LearningRecord(
        key, gate, status, summary, occurrences, nextState, firstSeen, seen, nextPrompted);
  }
}
