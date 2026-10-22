package dev.ward.domain.learning;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks recurring tool-rule findings and prompts once a rule recurs {@code threshold} times and is
 * fixed.
 */
public final class LearningPolicy {
  /**
   * Tools whose rule IDs name a specific rule; others report catch-all IDs with nothing to learn.
   */
  private static final Set<String> LEARNABLE_TOOLS = Set.of("checkstyle", "pmd", "gitleaks");

  private LearningPolicy() {}

  /**
   * Updates {@code ledger} with the rule findings of {@code report} at time {@code now}; gates that
   * were not evaluated keep their records.
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
      if (result.status() != GateStatus.PASSED && result.status() != GateStatus.FAILED) {
        continue;
      }
      final Map<String, String> rules = rules(result);
      if (result.status() == GateStatus.FAILED && rules.isEmpty()) {
        // No rule findings to compare against (e.g. no extractor ran), so nothing can resolve.
        continue;
      }
      rules.forEach(
          (rule, message) -> {
            final String key = result.name() + ":" + rule;
            records.put(key, failing(records.get(key), result.name(), rule, message, now));
          });
      prompts.addAll(resolve(records, result.name(), rules.keySet(), threshold, now));
    }
    return new LearningOutcome(
        new LearningLedger(ledger.formatVersion(), records), prompts, List.of());
  }

  /** Returns the first message of each learnable rule a failed gate reported, in report order. */
  private static Map<String, String> rules(final GateResult result) {
    final Map<String, String> rules = new LinkedHashMap<>();
    if (result.status() == GateStatus.FAILED) {
      for (final Finding finding : result.findings().items()) {
        if (LEARNABLE_TOOLS.contains(finding.tool()) && !finding.ruleId().equals(finding.tool())) {
          rules.putIfAbsent(finding.ruleId(), finding.message());
        }
      }
    }
    return rules;
  }

  /** Counts a new occurrence after a resolution, or refreshes an ongoing failure. */
  private static LearningRecord failing(
      final LearningRecord current,
      final String gate,
      final String rule,
      final String message,
      final Instant now) {
    if (current != null && current.state() != LearningState.RESOLVED) {
      return current.withState(LearningState.FAILING, now, current.prompted());
    }
    return new LearningRecord(
        gate + ":" + rule,
        gate,
        rule,
        truncate(message.isBlank() ? rule : message),
        current == null ? 1 : current.occurrences() + 1,
        LearningState.FAILING,
        current == null ? now : current.firstSeen(),
        now,
        current != null && current.prompted());
  }

  /** Resolves the failing records of {@code gate} whose rule this run no longer reported. */
  private static List<LearningPrompt> resolve(
      final Map<String, LearningRecord> records,
      final String gate,
      final Set<String> reported,
      final int threshold,
      final Instant now) {
    final List<LearningPrompt> prompts = new ArrayList<>();
    records.replaceAll(
        (key, record) -> {
          if (!record.gate().equals(gate)
              || record.state() != LearningState.FAILING
              || reported.contains(record.rule())) {
            return record;
          }
          final boolean emit = !record.prompted() && record.occurrences() >= threshold;
          if (emit) {
            prompts.add(
                new LearningPrompt(
                    record.key(),
                    record.gate(),
                    record.rule(),
                    record.occurrences(),
                    record.summary()));
          }
          return record.withState(LearningState.RESOLVED, now, record.prompted() || emit);
        });
    return prompts;
  }

  private static String truncate(final String value) {
    return value.length() <= LearningRecord.MAX_SUMMARY_LENGTH
        ? value
        : value.substring(0, LearningRecord.MAX_SUMMARY_LENGTH);
  }
}
