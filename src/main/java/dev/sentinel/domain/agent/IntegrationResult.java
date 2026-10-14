package dev.sentinel.domain.agent;

import java.util.List;
import java.util.Set;

public record IntegrationResult(
    Status status, List<String> changed, String message, Set<IntegrationCapability> capabilities) {
  public enum Status {
    CHANGED,
    ALREADY_PRESENT,
    REMOVED,
    CONFLICT,
    NOT_FOUND
  }

  public IntegrationResult(final Status status, final List<String> changed, final String message) {
    this(status, changed, message, Set.of());
  }

  public IntegrationResult {
    changed = List.copyOf(changed);
    capabilities = Set.copyOf(capabilities);
  }

  public IntegrationResult withCapabilities(final Set<IntegrationCapability> values) {
    return new IntegrationResult(status, changed, message, values);
  }
}
