package dev.sentinel.domain.agent;

/** Diagnostic state of a project-local agent integration. */
public record IntegrationHealth(Status status, String message) {
  public enum Status {
    INSTALLED,
    MISSING,
    PARTIAL,
    CONFLICT
  }
}
