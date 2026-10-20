package dev.sentinel.domain.agent;

/** Diagnostic state of a project-local agent integration. */
public record IntegrationHealth(Status status, String message) {
  /**
   * Whether an integration is installed, missing, partly installed, or blocked by user-owned files.
   */
  public enum Status {
    INSTALLED,
    MISSING,
    PARTIAL,
    CONFLICT
  }
}
