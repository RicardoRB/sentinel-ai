package dev.sentinel.domain.process;

import java.time.Duration;

public record CommandResult(
    int exitCode, String stdout, String stderr, Duration duration, String executionError) {
  public CommandResult(int exitCode, String stdout, String stderr, Duration duration) {
    this(exitCode, stdout, stderr, duration, null);
  }

  public boolean succeeded() {
    return exitCode == 0;
  }

  public boolean hasExecutionError() {
    return executionError != null && !executionError.isBlank();
  }
}
