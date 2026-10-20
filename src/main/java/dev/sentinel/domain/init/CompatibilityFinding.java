package dev.sentinel.domain.init;

/** Safe diagnostic describing a compatibility decision without project contents. */
public record CompatibilityFinding(
    String artifactId,
    String currentVersion,
    String selectedVersion,
    CompatibilityOutcome outcome,
    String message) {
  /**
   * Validates the finding.
   *
   * @throws IllegalArgumentException if the artifact or outcome is missing
   */
  public CompatibilityFinding {
    if (artifactId == null || artifactId.isBlank()) {
      throw new IllegalArgumentException("Plugin artifactId is required");
    }
    if (outcome == null) {
      throw new IllegalArgumentException("Compatibility outcome is required");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Compatibility message is required");
    }
  }

  /** Returns whether resolving this finding means changing the declared plugin version. */
  public boolean changesVersion() {
    return selectedVersion != null
        && !selectedVersion.equals(currentVersion)
        && outcome == CompatibilityOutcome.UPGRADE_RECOMMENDED;
  }
}
