package dev.sentinel.domain.traceability;

import java.util.List;

public record TraceabilityResult(
    Status status, String summary, List<TraceabilityFinding> findings) {
  public enum Status {
    PASSED,
    NO_SCENARIOS,
    FAILED
  }

  public TraceabilityResult {
    findings = List.copyOf(findings);
  }

  public boolean passed() {
    return status == Status.PASSED;
  }
}
