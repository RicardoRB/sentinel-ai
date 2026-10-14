package dev.sentinel.domain.init;

import java.util.List;

/** Complete, pre-write compatibility decision for one initialization invocation. */
public record CompatibilityPlan(List<CompatibilityFinding> findings) {
  public CompatibilityPlan {
    findings = findings == null ? List.of() : List.copyOf(findings);
  }

  public boolean hasUnresolvableFindings() {
    return findings.stream()
        .anyMatch(
            finding ->
                finding.outcome() == CompatibilityOutcome.UNKNOWN
                    || finding.outcome() == CompatibilityOutcome.UNRESOLVABLE
                    || finding.outcome() == CompatibilityOutcome.INCOMPATIBLE);
  }

  public List<CompatibilityFinding> changes() {
    return findings.stream().filter(CompatibilityFinding::changesVersion).toList();
  }
}
