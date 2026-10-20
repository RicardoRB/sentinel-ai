package dev.sentinel.domain.init;

/** A catalog entry describing the toolchain range supported by a plugin version. */
public record ManagedPluginCandidate(
    String artifactId,
    String version,
    int minimumJava,
    int maximumJava,
    String minimumMaven,
    String configurationStrategy) {
  /**
   * Validates the entry.
   *
   * @throws IllegalArgumentException if a required value is blank or the Java range is invalid
   */
  public ManagedPluginCandidate {
    requireText(artifactId, "Plugin artifactId is required");
    requireText(version, "Plugin version is required");
    if (minimumJava < 1 || maximumJava < minimumJava) {
      throw new IllegalArgumentException("Invalid Java compatibility range");
    }
    requireText(minimumMaven, "Minimum Maven version is required");
    requireText(configurationStrategy, "Configuration strategy is required");
  }

  private static void requireText(final String value, final String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
