package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.application.gate.CheckProgressListener;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.SeverityThreshold;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.terminal.TerminalCapabilities;
import java.io.PrintWriter;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;

/** Prints gate progress and the human-readable check summary. */
public class TextReportRenderer implements CheckProgressListener {
  private static final int RENDER_CAPACITY = 512;

  private final TerminalCapabilities terminal;
  private final String version;
  private Consumer<String> print = ignored -> {};
  private Consumer<String> println = ignored -> {};
  private Runnable flush = () -> {};

  /**
   * Creates the renderer. {@code terminal} decides whether in-place progress is shown, and {@code
   * version} is printed in the header.
   */
  @Inject
  public TextReportRenderer(
      final TerminalCapabilities terminal, @Named("sentinel.version") final String version) {
    this.terminal = terminal;
    this.version = version;
  }

  /** Creates a renderer for plain, non-interactive output. */
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

  /** Prints the header to {@code output} and sends later progress there. */
  public void begin(final PrintWriter output) {
    print = output::print;
    println = output::println;
    flush = output::flush;
    println.accept("Sentinel " + version);
    println.accept("");
    flush.run();
  }

  @Override
  public void gateStarted(final String name) {
    if (terminal.interactive()) {
      print.accept("… " + ReportText.displayName(name));
      flush.run();
    }
  }

  @Override
  public void gateFinished(final GateResult result) {
    if (terminal.interactive()) {
      print.accept("\r\033[2K");
    }
    println.accept(ReportText.gateLine(result));
    flush.run();
  }

  /** Prints the summary of {@code report}. */
  public void summary(final CheckReport report) {
    summary(report, null);
  }

  /** Prints the summary of {@code report} with its learning prompts. */
  public void summary(final CheckReport report, final LearningOutcome learning) {
    summary(report, learning, SeverityThreshold.WARNING);
  }

  /** Prints the summary of {@code report}, listing findings at or above {@code threshold}. */
  public void summary(
      final CheckReport report, final LearningOutcome learning, final SeverityThreshold threshold) {
    print.accept(ReportText.summary(report, learning, threshold));
    flush.run();
  }

  /** Returns the complete text report for {@code report}. */
  public String render(final CheckReport report) {
    return render(report, null);
  }

  /** Returns the complete text report with its learning prompts. */
  public String render(final CheckReport report, final LearningOutcome learning) {
    return render(report, learning, SeverityThreshold.WARNING);
  }

  /** Returns the complete text report, listing findings at or above {@code threshold}. */
  public String render(
      final CheckReport report, final LearningOutcome learning, final SeverityThreshold threshold) {
    final StringBuilder output = new StringBuilder(RENDER_CAPACITY);
    output.append("Sentinel ").append(version).append(ReportText.NL).append(ReportText.NL);
    for (final GateResult result : report.results()) {
      output.append(ReportText.gateLine(result)).append(ReportText.NL);
    }
    return output.append(ReportText.summary(report, learning, threshold)).toString();
  }
}
