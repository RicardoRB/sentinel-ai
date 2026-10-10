package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Human-readable text fragments of a check report. */
final class ReportText {
  static final String NL = System.lineSeparator();
  private static final int OUTPUT_TAIL_LINES = 40;
  private static final int SUMMARY_CAPACITY = 256;
  private static final Map<String, String> DISPLAY_NAMES =
      Map.ofEntries(
          Map.entry("checkstyle", "Checkstyle"),
          Map.entry("pmd", "PMD"),
          Map.entry("spotbugs", "SpotBugs"),
          Map.entry("owasp", "OWASP Dependency-Check"),
          Map.entry("dependency-check", "OWASP Dependency-Check"),
          Map.entry("gitleaks", "Gitleaks"));

  private ReportText() {}

  /** The status, tally, learning prompts, and failure details printed after the gate lines. */
  static String summary(final CheckReport report, final LearningOutcome learning) {
    final StringBuilder output = new StringBuilder(SUMMARY_CAPACITY);
    output
        .append(NL)
        .append("Quality Gate: ")
        .append(report.status())
        .append(NL)
        .append(tally(report))
        .append(NL);
    appendLearning(output, learning);
    for (final GateResult result : report.results()) {
      if (result.status() != GateStatus.PASSED && result.status() != GateStatus.SKIPPED) {
        appendFailure(output, result);
      }
    }
    return output.toString();
  }

  static String gateLine(final GateResult result) {
    final String marker =
        switch (result.status()) {
          case PASSED -> "✓";
          case SKIPPED -> "–";
          default -> "✗";
        };
    final String line = marker + " " + displayName(result.name());
    if (result.status() == GateStatus.UNAVAILABLE) {
      return line + " — unavailable";
    }
    if (result.status() == GateStatus.EXECUTION_ERROR) {
      return line + " — error";
    }
    if (result.status() == GateStatus.SKIPPED && result.summary() != null) {
      return line + " — " + result.summary();
    }
    if (result.status() == GateStatus.FAILED && result.errors() != null) {
      return line + " — " + result.errors() + " errors";
    }
    return line;
  }

  static String displayName(final String id) {
    return DISPLAY_NAMES.getOrDefault(id.toLowerCase(Locale.ROOT), id);
  }

  private static String tally(final CheckReport report) {
    final String tally =
        report.passedCount()
            + " passed · "
            + report.failedCount()
            + " failed · "
            + report.skippedCount()
            + " skipped";
    if (report.totalErrors().isEmpty() && report.totalWarnings().isEmpty()) {
      return tally;
    }
    return tally
        + " ("
        + report.totalErrors().orElse(0)
        + " errors · "
        + report.totalWarnings().orElse(0)
        + " warnings)";
  }

  private static void appendLearning(final StringBuilder output, final LearningOutcome learning) {
    if (learning == null || learning.prompts().isEmpty()) {
      return;
    }
    output.append(NL).append("Learning").append(NL);
    for (final LearningPrompt prompt : learning.prompts()) {
      output
          .append("• ")
          .append(displayName(prompt.gate()))
          .append(" (")
          .append(prompt.occurrences())
          .append(" occurrences): ")
          .append(prompt.instruction())
          .append(NL);
    }
  }

  private static void appendFailure(final StringBuilder output, final GateResult result) {
    output
        .append(NL)
        .append("Command:")
        .append(NL)
        .append(String.join(" ", result.command()))
        .append(NL);
    appendTail(output, "stdout", result.stdout());
    appendTail(output, "stderr", result.stderr());
  }

  private static void appendTail(
      final StringBuilder output, final String label, final String text) {
    if (text.isBlank()) {
      return;
    }
    final List<String> lines = text.stripTrailing().lines().toList();
    final int from = Math.max(0, lines.size() - OUTPUT_TAIL_LINES);
    output
        .append(NL)
        .append("Last ")
        .append(lines.size() - from)
        .append(" lines of ")
        .append(label)
        .append(':')
        .append(NL);
    for (final String line : lines.subList(from, lines.size())) {
      output.append(line).append(NL);
    }
  }
}
