package dev.sentinel.domain.doctor;

import java.nio.file.Path;

/** Outbound contract for observing host and project environment facts. */
@FunctionalInterface
public interface EnvironmentInspection {
  /**
   * Observes the executables on the path and the relevant environment values for {@code
   * projectRoot}.
   */
  EnvironmentFacts inspect(Path projectRoot);

  /** Inspects one configured executable without executing it. */
  default ExecutableObservation inspectExecutable(final Path projectRoot, final String executable) {
    final boolean available = inspect(projectRoot).hasExecutable(executable);
    return new ExecutableObservation(
        executable, Path.of(executable), available, available, available);
  }

  /** Returns whether the project-root OpenSpec marker is a directory. */
  default boolean hasOpenSpecDirectory(final Path projectRoot) {
    return false;
  }
}
