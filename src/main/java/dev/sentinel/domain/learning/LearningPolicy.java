package dev.sentinel.domain.learning;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LearningPolicy {
  private LearningPolicy() {}

  public static LearningOutcome apply(
      final LearningLedger ledger,
      final CheckReport report,
      final int threshold,
      final Instant now) {
    if (threshold < 1) {
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
      final String status = result.status().name();
      final String key = result.name() + ":" + status;
      final LearningRecord current = records.get(key);
      final String summary = truncate(summary(result));
      if (current == null || current.state() == LearningState.RESOLVED) {
        records.put(
            key,
            new LearningRecord(
                key,
                result.name(),
                status,
                summary,
                current == null ? 1 : current.occurrences() + 1,
                LearningState.FAILING,
                current == null ? now : current.firstSeen(),
                now,
                current != null && current.prompted()));
      } else {
        records.put(
            key,
            new LearningRecord(
                key,
                current.gate(),
                current.status(),
                current.summary(),
                current.occurrences(),
                LearningState.FAILING,
                current.firstSeen(),
                now,
                current.prompted()));
      }
    }
    return new LearningOutcome(
        new LearningLedger(ledger.formatVersion(), records), prompts, List.of());
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
    return value.length() <= 200 ? value : value.substring(0, 200);
  }
}
