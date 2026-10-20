package dev.sentinel.application.gate;

import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.Project;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** OpenSpec validation with explicit executable and project-directory prerequisites. */
public final class OpenSpecQualityGate implements QualityGate {
  public static final String ACTIVE = "openspec";
  public static final String ARCHIVED = "openspec-archived";

  private final String name;
  private final CommandExecutor executor;
  private final EnvironmentInspection environment;
  private final List<String> command;

  /**
   * Creates an OpenSpec validation gate; {@code environment} checks that the OpenSpec executable is
   * available.
   */
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
    final Optional<OpenSpecPrerequisites.Problem> problem =
        OpenSpecPrerequisites.check(environment, project.root(), command.getFirst());
    if (problem.isPresent()) {
      return problem.get().unavailable()
          ? unavailable(problem.get().message())
          : failure(problem.get().message());
    }
    return GateResult.from(name, command, executor.execute(command, project.root()));
  }

  private GateResult unavailable(final String message) {
    return GateResult.builder(name, GateStatus.UNAVAILABLE)
        .command(command)
        .exitCode(-1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr(message)
        .summary(message)
        .build();
  }

  private GateResult failure(final String message) {
    return GateResult.builder(name, GateStatus.FAILED)
        .command(command)
        .exitCode(1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr(message)
        .summary(message)
        .build();
  }
}
