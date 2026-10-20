package dev.sentinel.domain.traceability;

/** Source-level observation of annotations attached to one Java declaration. */
public record MappingObservation(
    String capabilityPath,
    String scenarioName,
    SourceLocation location,
    boolean supportedTest,
    String problem) {
  /** Replaces missing texts with empty ones. */
  public MappingObservation {
    if (capabilityPath == null) {
      capabilityPath = "";
    }
    if (scenarioName == null) {
      scenarioName = "";
    }
    if (problem == null) {
      problem = "";
    }
  }

  /**
   * Returns whether the annotation is on a supported test and names a capability and scenario
   * without problems.
   */
  public boolean validShape() {
    return supportedTest
        && !capabilityPath.isBlank()
        && !scenarioName.isBlank()
        && problem.isBlank();
  }

  /** Returns the scenario the annotation names. */
  public ScenarioIdentity identity() {
    return new ScenarioIdentity(capabilityPath, scenarioName);
  }
}
