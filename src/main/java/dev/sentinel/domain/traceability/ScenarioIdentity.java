package dev.sentinel.domain.traceability;

import java.util.Objects;

/** The exact, fully-qualified identity of an OpenSpec scenario. */
public record ScenarioIdentity(String capabilityPath, String scenarioName)
    implements Comparable<ScenarioIdentity> {
  /**
   * Validates the identity.
   *
   * @throws IllegalArgumentException if the capability path or scenario name is blank
   */
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
  public String toString() {
    return capabilityPath + " / " + scenarioName;
  }

  // Comparable records keep explicit equality that matches compareTo.
  @Override
  public boolean equals(final Object object) {
    return object instanceof ScenarioIdentity other
        && capabilityPath.equals(other.capabilityPath)
        && scenarioName.equals(other.scenarioName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(capabilityPath, scenarioName);
  }
}
