package dev.ward.domain.doctor;

import java.nio.file.Path;
import java.util.Optional;

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

  /** Returns whether Git ignores the project-root {@code openspec/} directory. */
  default boolean isOpenSpecDirectoryIgnored(final Path projectRoot) {
    return false;
  }

  /**
   * Returns the project-relative path of the Git-tracked OpenAPI spec of {@code projectRoot}: the
   * first tracked {@code openapi.yml}, {@code openapi.yaml}, or {@code openapi.json} under {@code
   * src/main/resources}, then at the project root.
   */
  default Optional<String> openApiSpec(final Path projectRoot) {
    return Optional.empty();
  }

  /** Returns how the project's Dependency-Check build configuration sets the OSS Index analyzer. */
  default OssIndexSetting ossIndexSetting(final Path projectRoot) {
    return OssIndexSetting.ABSENT;
  }
}
