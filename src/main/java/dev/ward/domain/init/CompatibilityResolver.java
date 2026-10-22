package dev.ward.domain.init;

import java.util.List;
import java.util.Map;

/** Pure, deterministic selection of compatible managed Maven plugin versions. */
public final class CompatibilityResolver {

  /**
   * Chooses, for each selected plugin, the newest catalog version compatible with the project's
   * toolchain.
   */
  public CompatibilityPlan resolve(
      final CompatibilityProjectSnapshot snapshot,
      final Map<String, List<ManagedPluginCandidate>> catalog,
      final List<String> selectedArtifacts) {
    return new CompatibilityPlan(
        selectedArtifacts.stream()
            .map(
                artifact ->
                    resolveOne(snapshot, catalog.getOrDefault(artifact, List.of()), artifact))
            .toList());
  }

  private CompatibilityFinding resolveOne(
      final CompatibilityProjectSnapshot snapshot,
      final List<ManagedPluginCandidate> candidates,
      final String artifact) {
    if (candidates.isEmpty()) {
      return new CompatibilityFinding(
          artifact,
          snapshot.pluginVersion(artifact).orElse(null),
          null,
          CompatibilityOutcome.UNRESOLVABLE,
          "No compatibility catalog entry exists for this managed Maven plugin.");
    }
    final String mavenVersion = snapshot.toolchain().mavenVersion().orElse(null);
    if (mavenVersion == null) {
      return new CompatibilityFinding(
          artifact,
          snapshot.pluginVersion(artifact).orElse(null),
          null,
          CompatibilityOutcome.UNKNOWN,
          "Maven version could not be determined; install Maven or provide a Maven Wrapper.");
    }
    final int javaVersion =
        snapshot.toolchain().compilerRelease().orElse(snapshot.toolchain().javaVersion());
    final List<ManagedPluginCandidate> compatible =
        candidates.stream()
            .filter(candidate -> supports(candidate, javaVersion, mavenVersion))
            .sorted((left, right) -> compareVersions(right.version(), left.version()))
            .toList();
    if (compatible.isEmpty()) {
      return new CompatibilityFinding(
          artifact,
          snapshot.pluginVersion(artifact).orElse(null),
          null,
          CompatibilityOutcome.INCOMPATIBLE,
          "No catalog version supports the detected Java and Maven toolchain.");
    }
    final String current = snapshot.pluginVersion(artifact).orElse(null);
    if (current != null
        && compatible.stream().anyMatch(candidate -> candidate.version().equals(current))) {
      return new CompatibilityFinding(
          artifact,
          current,
          current,
          CompatibilityOutcome.COMPATIBLE,
          "Existing plugin version is compatible with the detected toolchain.");
    }
    final String selected = compatible.getFirst().version();
    return new CompatibilityFinding(
        artifact,
        current,
        selected,
        current == null
            ? CompatibilityOutcome.COMPATIBLE
            : CompatibilityOutcome.UPGRADE_RECOMMENDED,
        current == null
            ? "Selected the highest compatible catalog version."
            : "Existing plugin version is incompatible; a compatible upgrade is available.");
  }

  private static boolean supports(
      final ManagedPluginCandidate candidate, final int javaVersion, final String mavenVersion) {
    return javaVersion >= candidate.minimumJava()
        && javaVersion <= candidate.maximumJava()
        && compareVersions(mavenVersion, candidate.minimumMaven()) >= 0;
  }

  static int compareVersions(final String left, final String right) {
    final String[] leftParts = left.split("[.-]");
    final String[] rightParts = right.split("[.-]");
    final int length = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < length; index++) {
      final int leftPart = numericPart(leftParts, index);
      final int rightPart = numericPart(rightParts, index);
      if (leftPart != rightPart) {
        return Integer.compare(leftPart, rightPart);
      }
    }
    return 0;
  }

  private static int numericPart(final String[] parts, final int index) {
    if (index >= parts.length) {
      return 0;
    }
    final String digits = parts[index].replaceAll("[^0-9].*", "");
    return digits.isEmpty() ? 0 : Integer.parseInt(digits);
  }
}
