package dev.ward.domain.init;

import dev.ward.domain.project.Project;
import java.util.Map;
import java.util.Optional;

/** Sanitized Maven project metadata supplied to the compatibility resolver. */
public record CompatibilityProjectSnapshot(
    Project project, ToolchainSnapshot toolchain, Map<String, String> pluginVersions) {
  /**
   * Validates the snapshot and copies the plugin versions.
   *
   * @throws IllegalArgumentException if the project or toolchain is missing
   */
  public CompatibilityProjectSnapshot {
    if (project == null || toolchain == null) {
      throw new IllegalArgumentException("Project and toolchain are required");
    }
    pluginVersions = pluginVersions == null ? Map.of() : Map.copyOf(pluginVersions);
  }

  /** Returns the version the build declares for {@code artifactId}, if any. */
  public Optional<String> pluginVersion(final String artifactId) {
    return Optional.ofNullable(pluginVersions.get(artifactId));
  }
}
