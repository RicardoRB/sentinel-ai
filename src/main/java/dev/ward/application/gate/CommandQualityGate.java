package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.ReportDirectories;
import dev.ward.domain.gate.ReportWriteException;
import dev.ward.domain.init.GateToolInstallCatalog;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Generic process-backed gate used by built-in and repository-defined gate registrations. */
public final class CommandQualityGate implements QualityGate {
  private static final Logger LOGGER = Logger.getLogger(CommandQualityGate.class.getName());
  private static final String REPORT_PATH_OPTION = "--report-path";

  private final String name;
  private final CommandExecutor executor;
  private final List<String> command;
  private final ReportDirectories reportDirectories;

  /** Creates a gate that runs {@code command} without collecting report files. */
  public CommandQualityGate(
      final String name, final CommandExecutor executor, final List<String> command) {
    this(name, executor, command, ReportDirectories.NONE);
  }

  /**
   * Creates a gate that runs {@code command} and keeps the reports it writes under {@code
   * reportDirectories}.
   */
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
    final String hint =
        command.isEmpty() ? null : GateToolInstallCatalog.installHint(command.getFirst());
    if (hint == null || result.status() != GateStatus.UNAVAILABLE) {
      return result;
    }
    return GateResult.builder(result.name(), result.status())
        .command(result.command())
        .exitCode(result.exitCode())
        .duration(result.duration())
        .stdout(result.stdout())
        .stderr(result.stderr())
        .summary(result.summary() + " Install " + command.getFirst() + ": " + hint)
        .build();
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
