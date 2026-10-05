package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;
import java.util.List;

public record CheckReport(Project project, List<GateResult> results) {

  public CheckReport {
    results = List.copyOf(results);
  }

  public GateStatus status() {
    return results.stream()
            .filter(result -> result.status() != GateStatus.SKIPPED)
            .allMatch(GateResult::passed)
        ? GateStatus.PASSED
        : GateStatus.FAILED;
  }

  public boolean passed() {
    return status() == GateStatus.PASSED;
  }
}
