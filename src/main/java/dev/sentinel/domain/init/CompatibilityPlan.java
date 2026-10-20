package dev.sentinel.domain.init;

import java.util.List;

/** Complete, pre-write compatibility decision for one initialization invocation. */
public record CompatibilityPlan(List<CompatibilityFinding> findings) {
  /** Copies the findings; a missing list means no findings. */
  public CompatibilityPlan {
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  /** Returns whether some plugin has no compatible version or could not be checked. */
  public boolean hasUnresolvableFindings() {
    return findings.stream()
        .anyMatch(
            finding ->
                finding.outcome() == CompatibilityOutcome.UNKNOWN
                    || finding.outcome() == CompatibilityOutcome.UNRESOLVABLE
                    || finding.outcome() == CompatibilityOutcome.INCOMPATIBLE);
  }

  /** Returns the findings that change a plugin version. */
  public List<CompatibilityFinding> changes() {
    return findings.stream().filter(CompatibilityFinding::changesVersion).toList();
  }
}
