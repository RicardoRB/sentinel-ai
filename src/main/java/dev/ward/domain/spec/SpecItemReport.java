package dev.ward.domain.spec;

import java.util.List;

/** Findings for one validated item or spec gate, identified by a human-readable title. */
public record SpecItemReport(String title, List<SpecFinding> findings) {

  /** Copies the findings. */
  public SpecItemReport {
    findings = List.copyOf(findings);
  }

  /** Returns whether the item has any finding. */
  public boolean hasFindings() {
    return !findings.isEmpty();
  }

  /** Returns how many findings have {@code level}. */
  public long count(final SpecFinding.Level level) {
    return findings.stream().filter(finding -> finding.level() == level).count();
  }
}
