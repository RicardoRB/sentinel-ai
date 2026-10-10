package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.application.gate.CheckProgressListener;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.terminal.TerminalCapabilities;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import javax.inject.Inject;
import javax.inject.Named;

public class TextReportRenderer implements CheckProgressListener {
  private static final int RENDER_CAPACITY = 512;

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
      writer.print("… " + ReportText.displayName(name));
      writer.flush();
    }
  }

  @Override
  public void gateFinished(final GateResult result) {
    if (terminal.interactive()) {
      writer.print("\r\033[2K");
    }
    writer.println(ReportText.gateLine(result));
    writer.flush();
  }

  public void summary(final CheckReport report) {
    summary(report, null);
  }

  public void summary(final CheckReport report, final LearningOutcome learning) {
    writer.print(ReportText.summary(report, learning));
    writer.flush();
  }

  public String render(final CheckReport report) {
    return render(report, null);
  }

  public String render(final CheckReport report, final LearningOutcome learning) {
    final StringBuilder output = new StringBuilder(RENDER_CAPACITY);
    output.append("Sentinel ").append(version).append(ReportText.NL).append(ReportText.NL);
    for (final GateResult result : report.results()) {
      output.append(ReportText.gateLine(result)).append(ReportText.NL);
    }
    return output.append(ReportText.summary(report, learning)).toString();
  }
}
