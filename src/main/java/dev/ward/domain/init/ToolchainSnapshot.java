package dev.ward.domain.init;

import dev.ward.domain.project.Framework;
import java.util.Optional;

/** Project facts used to choose a compatible managed Maven plugin version. */
public record ToolchainSnapshot(
    int javaVersion,
    Optional<String> mavenVersion,
    Optional<Integer> compilerRelease,
    Framework framework) {
  private static final int MINIMUM_SUPPORTED_JAVA = 1;

  /**
   * Validates the Java version; missing optional values mean unknown.
   *
   * @throws IllegalArgumentException if the Java version is not positive
   */
  public ToolchainSnapshot {
    if (javaVersion < MINIMUM_SUPPORTED_JAVA) {
      throw new IllegalArgumentException("Java version must be positive");
    }
    mavenVersion = mavenVersion == null ? Optional.empty() : mavenVersion;
    compilerRelease = compilerRelease == null ? Optional.empty() : compilerRelease;
    framework = framework == null ? Framework.NONE : framework;
  }
}
