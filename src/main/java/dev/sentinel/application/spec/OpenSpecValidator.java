package dev.sentinel.application.spec;

import dev.sentinel.application.gate.OpenSpecPrerequisites;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.spec.OpenSpecOutput;
import dev.sentinel.domain.spec.SpecFinding;
import dev.sentinel.domain.spec.SpecItem;
import dev.sentinel.domain.spec.SpecItemReport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Validates individual OpenSpec items with the project's configured OpenSpec executable. */
public class OpenSpecValidator {
  static final String TOOL = "openspec";

  private final CommandExecutor executor;
  private final EnvironmentInspection environment;

  @Inject
  public OpenSpecValidator(
      final CommandExecutor executor, final EnvironmentInspection environment) {
    this.executor = executor;
    this.environment = environment;
  }

  /**
   * Validates each item in order from the project root. When a prerequisite is unmet, nothing is
   * launched and a single report explains the problem.
   */
  public List<SpecItemReport> validate(
      final Path projectRoot, final String executable, final Collection<SpecItem> items) {
    final Optional<OpenSpecPrerequisites.Problem> problem =
        OpenSpecPrerequisites.check(environment, projectRoot, executable);
    if (problem.isPresent()) {
      return List.of(
          new SpecItemReport(
              TOOL,
              List.of(new SpecFinding(SpecFinding.Level.ERROR, null, problem.get().message()))));
    }
    final List<SpecItemReport> reports = new ArrayList<>();
    for (final SpecItem item : items) {
      reports.add(validate(projectRoot, executable, item));
    }
    return reports;
  }

  /** The direct argument vector used to validate one item strictly and non-interactively. */
  static List<String> command(final String executable, final SpecItem item) {
    return List.of(
        executable,
        "validate",
        item.id(),
        "--type",
        item.kind().type(),
        "--strict",
        "--no-interactive");
  }

  static String title(final SpecItem item) {
    return TOOL + " " + item.kind().type() + " " + item.id();
  }

  private SpecItemReport validate(
      final Path projectRoot, final String executable, final SpecItem item) {
    final CommandResult result = executor.execute(command(executable, item), projectRoot);
    if (result.hasExecutionError()) {
      return new SpecItemReport(
          title(item),
          List.of(
              new SpecFinding(
                  SpecFinding.Level.ERROR,
                  null,
                  "OpenSpec could not be run (" + result.executionError() + ").")));
    }
    return new SpecItemReport(
        title(item), OpenSpecOutput.parse(result.stdout(), result.stderr(), result.succeeded()));
  }
}
