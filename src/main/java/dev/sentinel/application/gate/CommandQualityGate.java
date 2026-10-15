package dev.sentinel.application.gate;

import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.gate.ReportDirectories;
import dev.sentinel.domain.gate.ReportWriteException;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Generic process-backed gate used by built-in and repository-defined gate registrations. */
public final class CommandQualityGate implements QualityGate {
  private static final Map<String, String> INSTALL_HINTS =
      Map.of(
          "semgrep", "https://semgrep.dev/docs/getting-started/",
          "gitleaks", "https://github.com/gitleaks/gitleaks#installing",
          "trivy", "https://trivy.dev/latest/getting-started/installation/",
          "zap-baseline.py", "https://www.zaproxy.org/docs/docker/baseline-scan/");

  private static final Logger LOGGER = Logger.getLogger(CommandQualityGate.class.getName());
  private static final String REPORT_PATH_OPTION = "--report-path";

  private final String name;
  private final CommandExecutor executor;
  private final List<String> command;
  private final ReportDirectories reportDirectories;

  public CommandQualityGate(
      final String name, final CommandExecutor executor, final List<String> command) {
    this(name, executor, command, ReportDirectories.NONE);
  }

  public CommandQualityGate(
      final String name,
      final CommandExecutor executor,
      final List<String> command,
      final ReportDirectories reportDirectories) {
    this.name = name;
    this.executor = executor;
    this.command = List.copyOf(command);
    this.reportDirectories = reportDirectories;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    prepareReportDirectory(project);
    final GateResult result =
        GateResult.from(name, command, executor.execute(command, project.root()));
    final String hint = command.isEmpty() ? null : INSTALL_HINTS.get(command.getFirst());
    if (hint == null || result.status() != GateStatus.UNAVAILABLE) {
      return result;
    }
    return new GateResult(
        result.name(),
        result.status(),
        result.command(),
        result.exitCode(),
        result.duration(),
        result.stdout(),
        result.stderr(),
        result.summary() + " Install " + command.getFirst() + ": " + hint);
  }

  /** Lets tools such as gitleaks write reports into build directories that do not exist yet. */
  private void prepareReportDirectory(final Project project) {
    final int option = command.indexOf(REPORT_PATH_OPTION);
    if (option < 0 || option + 1 >= command.size()) {
      return;
    }
    try {
      reportDirectories.prepareParent(project.root(), command.get(option + 1));
    } catch (ReportWriteException exception) {
      LOGGER.log(
          Level.WARNING,
          () ->
              "event=report-directory-failed gate="
                  + name
                  + " error="
                  + exception.getClass().getSimpleName());
    }
  }
}
