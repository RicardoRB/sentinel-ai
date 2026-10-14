package dev.sentinel.domain.init;

/** A catalog entry describing the toolchain range supported by a plugin version. */
public record ManagedPluginCandidate(
    String artifactId,
    String version,
    int minimumJava,
    int maximumJava,
    String minimumMaven,
    String configurationStrategy) {
  public ManagedPluginCandidate {
    if (artifactId == null || artifactId.isBlank()) {
      throw new IllegalArgumentException("Plugin artifactId is required");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Plugin version is required");
    }
    if (minimumJava < 1 || maximumJava < minimumJava) {
      throw new IllegalArgumentException("Invalid Java compatibility range");
    }
    if (minimumMaven == null || minimumMaven.isBlank()) {
      throw new IllegalArgumentException("Minimum Maven version is required");
    }
    if (configurationStrategy == null || configurationStrategy.isBlank()) {
      throw new IllegalArgumentException("Configuration strategy is required");
    }
  }
}
