package dev.sentinel.domain.spec;

import java.util.List;

/** Findings for one validated item or spec gate, identified by a human-readable title. */
public record SpecItemReport(String title, List<SpecFinding> findings) {

  public SpecItemReport {
    findings = List.copyOf(findings);
  }

  public boolean hasFindings() {
    return !findings.isEmpty();
  }

  public long count(final SpecFinding.Level level) {
    return findings.stream().filter(finding -> finding.level() == level).count();
  }
}
