package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.spec.SpecValidationReport;
import java.util.List;

/** Bounded, agent-facing messages written by the native hook. */
final class NativeHookMessages {
  private static final int MAX_FEEDBACK_CHARS = 8_192;
  private static final int MAX_ITEMS = 20;

  private NativeHookMessages() {}

  static String failureText(final CheckReport report, final SpecValidationReport specs) {
    final String quality =
        report.passed()
            ? ""
            : report.results().stream()
                .map(Object::toString)
                .limit(MAX_ITEMS)
                .reduce("Sentinel quality findings:", (a, b) -> a + "\n" + b);
    final String spec = specs.hasFindings() ? feedback(specs) : "";
    return bounded(
        List.of(quality, spec).stream()
            .filter(text -> !text.isBlank())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("Sentinel validation failed."));
  }

  static String feedback(final SpecValidationReport report) {
    return bounded(
        report.items().stream()
            .flatMap(item -> item.findings().stream().map(finding -> item.title() + ": " + finding))
            .limit(MAX_ITEMS)
            .reduce("Sentinel spec validation findings:", (a, b) -> a + "\n" + b));
  }

  static String safeMessage(final Exception exception) {
    final String message = exception.getMessage();
    return message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : bounded(message);
  }

  static String bounded(final String value) {
    return value.length() <= MAX_FEEDBACK_CHARS
        ? value
        : value.substring(0, MAX_FEEDBACK_CHARS) + "…";
  }
}
