package dev.sentinel.domain.traceability;

/** The exact, fully-qualified identity of an OpenSpec scenario. */
public record ScenarioIdentity(String capabilityPath, String scenarioName)
    implements Comparable<ScenarioIdentity> {
  public ScenarioIdentity {
    if (capabilityPath == null
        || capabilityPath.isBlank()
        || scenarioName == null
        || scenarioName.isBlank()) {
      throw new IllegalArgumentException("scenario identity must not be blank");
    }
  }

  @Override
  public int compareTo(final ScenarioIdentity other) {
    final int capability = capabilityPath.compareTo(other.capabilityPath);
    return capability == 0 ? scenarioName.compareTo(other.scenarioName) : capability;
  }

  @Override
  public int hashCode() {
    return 31 * capabilityPath.hashCode() + scenarioName.hashCode();
  }

  @Override
  public boolean equals(final Object object) {
    return object instanceof ScenarioIdentity other
        && capabilityPath.equals(other.capabilityPath)
        && scenarioName.equals(other.scenarioName);
  }

  @Override
  public String toString() {
    return capabilityPath + " / " + scenarioName;
  }
}
