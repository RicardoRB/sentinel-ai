package dev.sentinel.domain.traceability;

/** A live scenario and its spec header location. */
public record ScenarioDefinition(ScenarioIdentity identity, SourceLocation location)
    implements Comparable<ScenarioDefinition> {
  @Override
  public int compareTo(final ScenarioDefinition other) {
    final int identityOrder = identity.compareTo(other.identity);
    return identityOrder == 0 ? location.compareTo(other.location) : identityOrder;
  }

  @Override
  public boolean equals(final Object object) {
    return object instanceof ScenarioDefinition other
        && identity.equals(other.identity)
        && location.equals(other.location);
  }

  @Override
  public int hashCode() {
    return 31 * identity.hashCode() + location.hashCode();
  }
}
