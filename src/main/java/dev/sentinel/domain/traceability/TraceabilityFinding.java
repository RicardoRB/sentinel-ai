package dev.sentinel.domain.traceability;

/** A deterministic, source-located traceability failure. */
public record TraceabilityFinding(Kind kind, String message, SourceLocation location) {
  public enum Kind {
    MISSING,
    MALFORMED,
    UNKNOWN,
    AMBIGUOUS,
    UNREADABLE
  }
}
