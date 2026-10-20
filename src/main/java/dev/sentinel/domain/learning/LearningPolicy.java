package dev.sentinel.domain.learning;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracks consecutive gate failures and prompts once a failure repeats {@code threshold} times. */
public final class LearningPolicy {
  private LearningPolicy() {}

  /**
   * Updates {@code ledger} with the gate results of {@code report} at time {@code now}; skipped
   * gates keep their streak.
   *
   * @throws IllegalArgumentException if {@code threshold} is less than 1
   */
  public static LearningOutcome apply(
      final LearningLedger ledger,
      final CheckReport report,
      final int threshold,
      final Instant now) {
    if (threshold <= 0) {
      throw new IllegalArgumentException("Learning threshold must be at least 1");
    }
    final Map<String, LearningRecord> records = new LinkedHashMap<>(ledger.records());
    final List<LearningPrompt> prompts = new ArrayList<>();
    for (final GateResult result : report.results()) {
      if (result.status() == GateStatus.SKIPPED) {
        continue;
      }
      if (result.status() == GateStatus.PASSED) {
        resolve(records, result.name(), threshold, now, prompts);
        continue;
      }
      final String key = result.name() + ":" + result.status().name();
      records.put(key, failing(key, records.get(key), result, now));
    }
    return new LearningOutcome(
        new LearningLedger(ledger.formatVersion(), records), prompts, List.of());
  }

  /** Counts a new occurrence after a resolution, or refreshes an ongoing failure. */
  private static LearningRecord failing(
      final String key, final LearningRecord current, final GateResult result, final Instant now) {
    if (current != null && current.state() != LearningState.RESOLVED) {
      return new LearningRecord(
          key,
          current.gate(),
          current.status(),
          current.summary(),
          current.occurrences(),
          LearningState.FAILING,
          current.firstSeen(),
          now,
          current.prompted());
    }
    return new LearningRecord(
        key,
        result.name(),
        result.status().name(),
        truncate(summary(result)),
        current == null ? 1 : current.occurrences() + 1,
        LearningState.FAILING,
        current == null ? now : current.firstSeen(),
        now,
        current != null && current.prompted());
  }

  private static void resolve(
      final Map<String, LearningRecord> records,
      final String gate,
      final int threshold,
      final Instant now,
      final List<LearningPrompt> prompts) {
    records.replaceAll(
        (key, record) -> {
          if (!record.gate().equals(gate) || record.state() != LearningState.FAILING) {
            return record;
          }
          final boolean emit = !record.prompted() && record.occurrences() >= threshold;
          if (emit) {
            prompts.add(
                new LearningPrompt(
                    record.key(), record.gate(), record.occurrences(), record.summary()));
          }
          return record.withState(LearningState.RESOLVED, now, record.prompted() || emit);
        });
  }

  private static String summary(final GateResult result) {
    if (result.summary() != null && !result.summary().isBlank()) {
      return result.summary();
    }
    return result.status().name();
  }

  private static String truncate(final String value) {
    return value.length() <= LearningRecord.MAX_SUMMARY_LENGTH
        ? value
        : value.substring(0, LearningRecord.MAX_SUMMARY_LENGTH);
  }
}
