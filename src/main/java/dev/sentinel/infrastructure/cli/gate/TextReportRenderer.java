package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.application.gate.CheckProgressListener;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import dev.sentinel.domain.terminal.TerminalCapabilities;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;

public class TextReportRenderer implements CheckProgressListener {
  private static final int OUTPUT_TAIL_LINES = 40;
  private static final Map<String, String> DISPLAY_NAMES =
      Map.ofEntries(
          Map.entry("checkstyle", "Checkstyle"),
          Map.entry("pmd", "PMD"),
          Map.entry("spotbugs", "SpotBugs"),
          Map.entry("owasp", "OWASP Dependency-Check"),
          Map.entry("dependency-check", "OWASP Dependency-Check"),
          Map.entry("gitleaks", "Gitleaks"));

  private final TerminalCapabilities terminal;
  private final String version;
  private PrintWriter writer;

  @Inject
  public TextReportRenderer(
      final TerminalCapabilities terminal, @Named("sentinel.version") final String version) {
    this.terminal = terminal;
    this.version = version;
  }

  public TextReportRenderer() {
    this(
        new TerminalCapabilities() {
          @Override
          public boolean interactive() {
            return false;
          }

          @Override
          public boolean colorEnabled() {
            return false;
          }
        },
        "dev");
  }

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "The renderer writes to the command-owned output stream during one invocation.")
  public void begin(final PrintWriter output) {
    writer = output;
    writer.println("Sentinel " + version);
    writer.println();
    writer.flush();
  }

  @Override
  public void gateStarted(final String name) {
    if (terminal.interactive()) {
      writer.print("… " + displayName(name));
      writer.flush();
    }
  }

  @Override
  public void gateFinished(final GateResult result) {
    if (terminal.interactive()) {
      writer.print("\r\033[2K");
    }
    writer.println(gateLine(result));
    writer.flush();
  }

  public void summary(final CheckReport report) {
    summary(report, null);
  }

  public void summary(final CheckReport report, final LearningOutcome learning) {
    writer.println();
    writer.println("Quality Gate: " + report.status());
    String tally =
        report.passedCount()
            + " passed · "
            + report.failedCount()
            + " failed · "
            + report.skippedCount()
            + " skipped";
    if (report.totalErrors().isPresent() || report.totalWarnings().isPresent()) {
      tally +=
          " ("
              + report.totalErrors().orElse(0)
              + " errors · "
              + report.totalWarnings().orElse(0)
              + " warnings)";
    }
    writer.println(tally);
    appendLearning(writer, learning);
    for (final GateResult result : report.results()) {
      if (result.status() != GateStatus.PASSED && result.status() != GateStatus.SKIPPED) {
        appendFailure(writer, result);
      }
    }
    writer.flush();
  }

  public String render(final CheckReport report) {
    return render(report, null);
  }

  public String render(final CheckReport report, final LearningOutcome learning) {
    final StringBuilder output = new StringBuilder();
    output
        .append("Sentinel ")
        .append(version)
        .append(System.lineSeparator())
        .append(System.lineSeparator());
    for (final GateResult result : report.results()) {
      output.append(gateLine(result)).append(System.lineSeparator());
    }
    output
        .append(System.lineSeparator())
        .append("Quality Gate: ")
        .append(report.status())
        .append(System.lineSeparator());
    String tally =
        report.passedCount()
            + " passed · "
            + report.failedCount()
            + " failed · "
            + report.skippedCount()
            + " skipped";
    if (report.totalErrors().isPresent() || report.totalWarnings().isPresent()) {
      tally +=
          " ("
              + report.totalErrors().orElse(0)
              + " errors · "
              + report.totalWarnings().orElse(0)
              + " warnings)";
    }
    output.append(tally).append(System.lineSeparator());
    appendLearning(output, learning);
    for (final GateResult result : report.results()) {
      if (result.status() != GateStatus.PASSED && result.status() != GateStatus.SKIPPED) {
        appendFailure(output, result);
      }
    }
    return output.toString();
  }

  private static void appendLearning(final Appendable output, final LearningOutcome learning) {
    if (learning == null || learning.prompts().isEmpty()) {
      return;
    }
    try {
      output.append(System.lineSeparator()).append("Learning").append(System.lineSeparator());
      for (final LearningPrompt prompt : learning.prompts()) {
        output
            .append("• ")
            .append(displayName(prompt.gate()))
            .append(" (" + prompt.occurrences() + " occurrences): ")
            .append(prompt.instruction())
            .append(System.lineSeparator());
      }
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String gateLine(final GateResult result) {
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

  private static String displayName(final String id) {
    return DISPLAY_NAMES.getOrDefault(id.toLowerCase(Locale.ROOT), id);
  }

  private static void appendFailure(final Appendable output, final GateResult result) {
    try {
      output
          .append(System.lineSeparator())
          .append("Command:")
          .append(System.lineSeparator())
          .append(String.join(" ", result.command()))
          .append(System.lineSeparator());
      appendTail(output, "stdout", result.stdout());
      appendTail(output, "stderr", result.stderr());
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void appendTail(final Appendable output, final String label, final String text)
      throws IOException {
    if (text.isBlank()) {
      return;
    }
    final List<String> lines = text.stripTrailing().lines().toList();
    final int from = Math.max(0, lines.size() - OUTPUT_TAIL_LINES);
    output
        .append(System.lineSeparator())
        .append("Last ")
        .append(String.valueOf(lines.size() - from))
        .append(" lines of ")
        .append(label)
        .append(':')
        .append(System.lineSeparator());
    for (final String line : lines.subList(from, lines.size())) {
      output.append(line).append(System.lineSeparator());
    }
  }
}
