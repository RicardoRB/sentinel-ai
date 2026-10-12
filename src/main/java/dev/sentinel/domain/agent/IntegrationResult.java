package dev.sentinel.domain.agent;

import java.util.ArrayList;
import java.util.List;

public record IntegrationResult(
    Status status, List<String> changed, String message, List<String> warnings) {
  public enum Status {
    CHANGED,
    ALREADY_PRESENT,
    REMOVED,
    CONFLICT,
    NOT_FOUND,
    /** A prerequisite is missing, so nothing was written. */
    UNAVAILABLE
  }

  public IntegrationResult {
    changed = List.copyOf(changed);
    warnings = List.copyOf(warnings);
  }

  public IntegrationResult(final Status status, final List<String> changed, final String message) {
    this(status, changed, message, List.of());
  }

  /** Returns this result with {@code warning} appended. */
  public IntegrationResult withWarning(final String warning) {
    final List<String> combined = new ArrayList<>(warnings);
    combined.add(warning);
    return new IntegrationResult(status, changed, message, combined);
  }

  public boolean failed() {
    return status == Status.CONFLICT || status == Status.UNAVAILABLE;
  }
}
