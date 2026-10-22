package dev.ward.domain.provenance;

import java.util.Comparator;
import java.util.List;

/** Complete blocking enforcement result and its process exit semantics. */
public record EnforcementReport(List<EnforcementFinding> findings, int exitCode) {
  /** Sorts the findings by category and path; a negative exit code becomes 2. */
  public EnforcementReport {
    findings =
        List.copyOf(
            (findings == null ? List.<EnforcementFinding>of() : findings)
                .stream()
                    .sorted(
                        Comparator.comparing(EnforcementFinding::category)
                            .thenComparing(EnforcementFinding::path))
                    .toList());
    exitCode = exitCode < 0 ? 2 : exitCode;
  }

  /** Returns whether enforcement exited with 0 and reported no findings. */
  public boolean passed() {
    return exitCode == 0 && findings.isEmpty();
  }
}
