package dev.sentinel.domain.traceability;

/** Source-level observation of annotations attached to one Java declaration. */
public record MappingObservation(
    String capabilityPath,
    String scenarioName,
    SourceLocation location,
    boolean supportedTest,
    String problem) {
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

  public boolean validShape() {
    return supportedTest
        && !capabilityPath.isBlank()
        && !scenarioName.isBlank()
        && problem.isBlank();
  }

  public ScenarioIdentity identity() {
    return new ScenarioIdentity(capabilityPath, scenarioName);
  }
}
