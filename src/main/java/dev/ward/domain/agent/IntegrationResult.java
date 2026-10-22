package dev.ward.domain.agent;

import java.util.List;
import java.util.Set;

/**
 * The outcome of installing or removing an integration.
 *
 * @param status what happened
 * @param changed the files that were written or removed
 * @param message a summary for the user
 * @param capabilities what the installed integration enforces
 */
public record IntegrationResult(
    Status status, List<String> changed, String message, Set<IntegrationCapability> capabilities) {
  /** What an integration run did. */
  public enum Status {
    CHANGED,
    ALREADY_PRESENT,
    REMOVED,
    CONFLICT,
    NOT_FOUND
  }

  /** Creates a result without capabilities. */
  public IntegrationResult(final Status status, final List<String> changed, final String message) {
    this(status, changed, message, Set.of());
  }

  /** Copies the files and capabilities. */
  public IntegrationResult {
    changed = List.copyOf(changed);
    capabilities = Set.copyOf(capabilities);
  }

  /** Returns a copy carrying {@code values} as capabilities. */
  public IntegrationResult withCapabilities(final Set<IntegrationCapability> values) {
    return new IntegrationResult(status, changed, message, values);
  }
}
