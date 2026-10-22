package dev.ward.domain.traceability;

import java.util.List;

/**
 * The traceability verdict.
 *
 * @param status the overall status
 * @param summary a one-line summary
 * @param findings the policy findings
 */
public record TraceabilityResult(
    Status status, String summary, List<TraceabilityFinding> findings) {
  /** Whether every scenario is mapped, there were no scenarios, or mappings are incomplete. */
  public enum Status {
    PASSED,
    NO_SCENARIOS,
    FAILED
  }

  /** Copies the findings. */
  public TraceabilityResult {
    findings = List.copyOf(findings);
  }

  /** Returns whether the status is PASSED. */
  public boolean passed() {
    return status == Status.PASSED;
  }
}
