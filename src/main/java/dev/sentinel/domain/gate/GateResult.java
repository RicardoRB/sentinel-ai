package dev.sentinel.domain.gate;

import dev.sentinel.domain.process.CommandResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record GateResult(
    String name,
    GateStatus status,
    List<String> command,
    int exitCode,
    Duration duration,
    String stdout,
    String stderr,
    String summary,
    Integer errors,
    Integer warnings,
    GateFindings findings) {

  public GateResult {
    command = List.copyOf(command);
    findings = Objects.requireNonNullElse(findings, GateFindings.NONE);
  }

  public GateResult(
      final String name,
      final GateStatus status,
      final List<String> command,
      final int exitCode,
      final Duration duration,
      final String stdout,
      final String stderr,
      final String summary,
      final Integer errors,
      final Integer warnings) {
    this(
        name,
        status,
        command,
        exitCode,
        duration,
        stdout,
        stderr,
        summary,
        errors,
        warnings,
        GateFindings.NONE);
  }

  public GateResult(
      final String name,
      final GateStatus status,
      final List<String> command,
      final int exitCode,
      final Duration duration,
      final String stdout,
      final String stderr,
      final String summary) {
    this(name, status, command, exitCode, duration, stdout, stderr, summary, null, null);
  }

  public GateResult(
      final String name,
      final GateStatus status,
      final List<String> command,
      final int exitCode,
      final Duration duration,
      final String stdout,
      final String stderr) {
    this(name, status, command, exitCode, duration, stdout, stderr, null);
  }

  public static GateResult from(
      final String name, final List<String> command, final CommandResult result) {
    final GateStatus status =
        result.hasExecutionError()
                && result.exitCode() == -1
                && (result.executionError().startsWith("PROCESS_START_FAILURE")
                    || !result.executionError().contains("_"))
            ? GateStatus.UNAVAILABLE
            : result.hasExecutionError()
                ? GateStatus.EXECUTION_ERROR
                : result.succeeded() ? GateStatus.PASSED : GateStatus.FAILED;
    return new GateResult(
        name,
        status,
        List.copyOf(command),
        result.exitCode(),
        result.duration(),
        result.stdout(),
        result.stderr(),
        result.executionError());
  }

  /** Returns a copy of this result carrying {@code gateFindings}. */
  public GateResult withFindings(final GateFindings gateFindings) {
    return new GateResult(
        name,
        status,
        command,
        exitCode,
        duration,
        stdout,
        stderr,
        summary,
        errors,
        warnings,
        gateFindings);
  }

  public boolean passed() {
    return status == GateStatus.PASSED;
  }
}
