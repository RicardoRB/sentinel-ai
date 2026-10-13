package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import dev.sentinel.domain.spec.SpecReportRenderer;
import dev.sentinel.domain.spec.SpecValidationReport;
import java.util.ArrayList;
import java.util.List;

/** Plain-text feedback the Claude Code hook gives the agent. */
final class ClaudeHookFeedback {
  private static final String SPEC_HEADER = "Sentinel spec validation found problems:\n";

  private ClaudeHookFeedback() {}

  /** Failure lines for stderr: quality failures, spec findings, then learning prompts. */
  static List<String> failures(
      final CheckReport report, final SpecValidationReport specs, final LearningOutcome learning) {
    final List<String> lines = new ArrayList<>();
    if (!report.passed()) {
      lines.add(qualityFailures(report));
    }
    if (specs.hasFindings()) {
      lines.add(specFindings(specs));
    }
    learning.prompts().forEach(prompt -> lines.add(prompt.instruction()));
    return lines;
  }

  static String specFindings(final SpecValidationReport specs) {
    return SPEC_HEADER + SpecReportRenderer.render(specs);
  }

  static String learningContext(final LearningOutcome learning) {
    return learning.prompts().stream()
        .map(LearningPrompt::instruction)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");
  }

  private static String qualityFailures(final CheckReport report) {
    return report.results().stream()
        .filter(result -> !result.passed() && result.status() != GateStatus.SKIPPED)
        .map(
            result ->
                result.name()
                    + ": "
                    + (result.summary() == null ? result.status() : result.summary()))
        .reduce((a, b) -> a + System.lineSeparator() + b)
        .orElse("Sentinel quality check failed");
  }
}
