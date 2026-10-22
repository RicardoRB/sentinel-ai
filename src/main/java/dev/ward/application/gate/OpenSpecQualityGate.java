package dev.ward.application.gate;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
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
  private final String root;

  /**
   * Creates an OpenSpec validation gate; {@code environment} checks that the OpenSpec executable is
   * available.
   */
  public OpenSpecQualityGate(
      final String name,
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final List<String> command) {
    this(name, executor, environment, command, null);
  }

  /**
   * Creates an OpenSpec validation gate that runs from {@code root}, a project-relative directory,
   * or from the project root when {@code root} is {@code null}.
   */
  public OpenSpecQualityGate(
      final String name,
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final List<String> command,
      final String root) {
    this.name = name;
    this.executor = executor;
    this.environment = environment;
    this.command = List.copyOf(command);
    this.root = root;
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
    final Path directory =
        root == null ? project.root() : project.root().resolve(root).toAbsolutePath().normalize();
    if (root != null
        && !directory.startsWith(project.repositoryRoot().toAbsolutePath().normalize())) {
      return failure(
          "OpenSpec root '" + root + "' resolves outside the repository. Fix ward.toml.");
    }
    final Optional<OpenSpecPrerequisites.Problem> problem =
        OpenSpecPrerequisites.check(
            environment,
            directory,
            command.getFirst(),
            root == null ? "project-root" : "configured root '" + root + "'");
    if (problem.isPresent()) {
      return problem.get().unavailable()
          ? unavailable(problem.get().message())
          : failure(problem.get().message());
    }
    return GateResult.from(name, command, executor.execute(command, directory));
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
