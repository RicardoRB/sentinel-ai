package dev.ward.application.agent;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.List;

/** Runs the existing machine-readable Ward check for an agent edit/write event. */
public final class EditWriteGuardRunner {
  private static final List<String> CHECK = List.of("ward", "check", "--format", "json");

  private final CommandExecutor executor;

  /** Creates the runner; {@code executor} runs the Ward CLI. */
  public EditWriteGuardRunner(final CommandExecutor executor) {
    this.executor = executor;
  }

  /** Runs {@code ward check --format json} in {@code projectRoot} and returns its outcome. */
  public GuardRunResult run(final Path projectRoot) {
    final CommandResult result = executor.execute(CHECK, projectRoot);
    final String output =
        result.stdout() == null || result.stdout().isBlank() ? result.stderr() : result.stdout();
    return new GuardRunResult(
        result.succeeded() && !result.hasExecutionError(),
        output,
        result.exitCode(),
        result.executionError());
  }

  /**
   * The outcome of the guard check.
   *
   * @param succeeded whether the check passed
   * @param output the JSON report, or stderr when stdout is empty
   * @param exitCode the process exit code
   * @param executionError why the process could not run, if it could not
   */
  public record GuardRunResult(
      boolean succeeded, String output, int exitCode, String executionError) {}
}
