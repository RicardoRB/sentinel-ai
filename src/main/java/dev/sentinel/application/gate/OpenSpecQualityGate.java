package dev.sentinel.application.gate;

import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.ExecutableObservation;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.Project;
import java.time.Duration;
import java.util.List;

/** OpenSpec validation with explicit executable and project-directory prerequisites. */
public final class OpenSpecQualityGate implements QualityGate {
  public static final String ACTIVE = "openspec";
  public static final String ARCHIVED = "openspec-archived";

  private final String name;
  private final CommandExecutor executor;
  private final EnvironmentInspection environment;
  private final List<String> command;

  public OpenSpecQualityGate(
      final String name,
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final List<String> command) {
    this.name = name;
    this.executor = executor;
    this.environment = environment;
    this.command = List.copyOf(command);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    if (command.isEmpty()) {
      return failure("OpenSpec validation requires a command.");
    }
    final String executable = command.getFirst();
    final ExecutableObservation observation =
        environment.inspectExecutable(project.root(), executable);
    final boolean directory = environment.hasOpenSpecDirectory(project.root());
    if (!observation.usable()) {
      return unavailable(
          "OpenSpec executable '"
              + executable
              + "' is unavailable. Install OpenSpec or configure an executable path."
              + (directory
                  ? ""
                  : " The project-root openspec/ directory is also missing or not a directory."));
    }
    if (!directory) {
      return failure(
          "Project-root openspec/ directory is missing or is not a directory. Initialize the project manually.");
    }
    return GateResult.from(name, command, executor.execute(command, project.root()));
  }

  private GateResult unavailable(final String message) {
    return new GateResult(
        name, GateStatus.UNAVAILABLE, command, -1, Duration.ZERO, "", message, message);
  }

  private GateResult failure(final String message) {
    return new GateResult(name, GateStatus.FAILED, command, 1, Duration.ZERO, "", message, message);
  }
}
