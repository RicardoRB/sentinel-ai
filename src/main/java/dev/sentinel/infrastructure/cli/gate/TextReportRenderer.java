package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;

public class TextReportRenderer {

  @Inject
  public TextReportRenderer() {}

  private static final int OUTPUT_TAIL_LINES = 40;

  public String render(CheckReport report) {
    String nl = System.lineSeparator();
    StringBuilder sb = new StringBuilder();
    sb.append("Sentinel").append(nl).append(nl);
    for (GateResult result : report.results()) {
      sb.append(
          String.format(
              Locale.ROOT,
              "%s %-12s %-9s %s%n",
              result.passed() ? "✓" : "✗",
              result.name(),
              result.status(),
              seconds(result.duration())));
    }
    sb.append(nl).append("Quality gate: ").append(report.status()).append(nl);

    for (GateResult result : report.results()) {
      if (result.passed()) {
        continue;
      }
      sb.append(nl)
          .append("Command:")
          .append(nl)
          .append(String.join(" ", result.command()))
          .append(nl);
      appendTail(sb, "stdout", result.stdout());
      appendTail(sb, "stderr", result.stderr());
    }
    return sb.toString();
  }

  private static void appendTail(StringBuilder sb, String label, String output) {
    if (output.isBlank()) {
      return;
    }
    List<String> lines = output.stripTrailing().lines().toList();
    int from = Math.max(0, lines.size() - OUTPUT_TAIL_LINES);
    sb.append(System.lineSeparator())
        .append("Last ")
        .append(lines.size() - from)
        .append(" lines of ")
        .append(label)
        .append(':')
        .append(System.lineSeparator());
    lines
        .subList(from, lines.size())
        .forEach(line -> sb.append(line).append(System.lineSeparator()));
  }

  private static String seconds(Duration duration) {
    return String.format(Locale.ROOT, "%.2fs", duration.toMillis() / 1000.0);
  }
}
