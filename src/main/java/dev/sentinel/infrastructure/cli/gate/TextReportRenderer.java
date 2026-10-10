package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.application.gate.CheckProgressListener;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.terminal.TerminalCapabilities;
import java.io.PrintWriter;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;

public class TextReportRenderer implements CheckProgressListener {
  private static final int RENDER_CAPACITY = 512;

  private final TerminalCapabilities terminal;
  private final String version;
  private Consumer<String> print = ignored -> {};
  private Consumer<String> println = ignored -> {};
  private Runnable flush = () -> {};

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

  public void summary(final CheckReport report) {
    summary(report, null);
  }

  public void summary(final CheckReport report, final LearningOutcome learning) {
    print.accept(ReportText.summary(report, learning));
    flush.run();
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
