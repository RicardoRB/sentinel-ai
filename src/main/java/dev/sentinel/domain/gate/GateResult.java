package dev.sentinel.domain.gate;

import dev.sentinel.domain.process.CommandResult;
import java.time.Duration;
import java.util.List;

public record GateResult(
    String name,
    GateStatus status,
    List<String> command,
    int exitCode,
    Duration duration,
    String stdout,
    String stderr,
    String summary) {

  public GateResult {
    command = List.copyOf(command);
  }

  public GateResult(
      String name,
      GateStatus status,
      List<String> command,
      int exitCode,
      Duration duration,
      String stdout,
      String stderr) {
    this(name, status, command, exitCode, duration, stdout, stderr, null);
  }

  public static GateResult from(String name, List<String> command, CommandResult result) {
    GateStatus status =
        result.hasExecutionError() && result.exitCode() == -1
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

  public boolean passed() {
    return status == GateStatus.PASSED;
  }
}
