package dev.sentinel.domain.init;

import dev.sentinel.domain.project.Project;
import java.util.Map;
import java.util.Optional;

/** Sanitized Maven project metadata supplied to the compatibility resolver. */
public record CompatibilityProjectSnapshot(
    Project project, ToolchainSnapshot toolchain, Map<String, String> pluginVersions) {
  public CompatibilityProjectSnapshot {
    if (project == null || toolchain == null) {
      throw new IllegalArgumentException("Project and toolchain are required");
    }
    pluginVersions = pluginVersions == null ? Map.of() : Map.copyOf(pluginVersions);
  }

  public Optional<String> pluginVersion(final String artifactId) {
    return Optional.ofNullable(pluginVersions.get(artifactId));
  }
}
