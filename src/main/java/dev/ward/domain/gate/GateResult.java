package dev.ward.domain.gate;

import dev.ward.domain.process.CommandResult;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of one quality gate: its status, the command it ran, captured output, and any
 * structured findings.
 */
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
    GateFindings findings,
    GateScope scope) {

  /** Copies {@code command} and replaces missing findings with {@link GateFindings#NONE}. */
  public GateResult {
    command = List.copyOf(command);
    findings = Objects.requireNonNullElse(findings, GateFindings.NONE);
  }

  /** Starts a result for gate {@code name} with {@code status}; unset fields keep defaults. */
  public static Builder builder(final String name, final GateStatus status) {
    return new Builder(name, status);
  }

  /**
   * Builds the result of gate {@code name} from a finished process: unavailable when the executable
   * could not start, an execution error when it failed otherwise, and passed or failed by exit
   * code.
   */
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
    return builder(name, status)
        .command(List.copyOf(command))
        .exitCode(result.exitCode())
        .duration(result.duration())
        .stdout(result.stdout())
        .stderr(result.stderr())
        .summary(result.executionError())
        .build();
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
        gateFindings,
        scope);
  }

  /** Returns a copy carrying the partial-check scope. */
  public GateResult withScope(final GateScope gateScope) {
    return new GateResult(
        name, status, command, exitCode, duration, stdout, stderr, summary, errors, warnings,
        findings, gateScope);
  }

  /** Returns whether the gate passed. */
  public boolean passed() {
    return status == GateStatus.PASSED;
  }

  /** Accumulates the optional parts of a {@link GateResult}. */
  public static final class Builder {
    private final String name;
    private final GateStatus status;
    private List<String> command = List.of();
    private int exitCode;
    private Duration duration = Duration.ZERO;
    private String stdout = "";
    private String stderr = "";
    private String summary;
    private Integer errors;
    private Integer warnings;

    private Builder(final String name, final GateStatus status) {
      this.name = name;
      this.status = status;
    }

    /** Sets the argument vector the gate ran. */
    public Builder command(final List<String> value) {
      command = List.copyOf(value);
      return this;
    }

    /** Sets the process exit code. */
    public Builder exitCode(final int value) {
      exitCode = value;
      return this;
    }

    /** Sets how long the gate ran. */
    public Builder duration(final Duration value) {
      duration = value;
      return this;
    }

    /** Sets the captured standard output. */
    public Builder stdout(final String value) {
      stdout = value;
      return this;
    }

    /** Sets the captured standard error. */
    public Builder stderr(final String value) {
      stderr = value;
      return this;
    }

    /** Sets the one-line summary shown in reports. */
    public Builder summary(final String value) {
      summary = value;
      return this;
    }

    /** Sets the number of errors the tool reported, or {@code null} when unknown. */
    public Builder errors(final Integer value) {
      errors = value;
      return this;
    }

    /** Sets the number of warnings the tool reported, or {@code null} when unknown. */
    public Builder warnings(final Integer value) {
      warnings = value;
      return this;
    }

    /** Returns the result, without findings or partial-check scope. */
    public GateResult build() {
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
          GateFindings.NONE,
          null);
    }
  }
}
