package dev.sentinel.domain.traceability;

/** A deterministic, source-located traceability failure. */
public record TraceabilityFinding(Kind kind, String message, SourceLocation location) {
  /** Why a scenario or mapping failed the policy. */
  public enum Kind {
    MISSING,
    MALFORMED,
    UNKNOWN,
    AMBIGUOUS,
    UNREADABLE
  }
}
