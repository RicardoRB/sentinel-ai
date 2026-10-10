package dev.sentinel.domain.learning;

import java.time.Instant;
import java.util.Objects;

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

  public LearningRecord withState(
      final LearningState nextState, final Instant seen, final boolean nextPrompted) {
    return new LearningRecord(
        key, gate, status, summary, occurrences, nextState, firstSeen, seen, nextPrompted);
  }
}
