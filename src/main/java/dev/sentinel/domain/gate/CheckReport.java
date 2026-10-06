package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

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

  public long passedCount() {
    return results.stream().filter(result -> result.status() == GateStatus.PASSED).count();
  }

  public long failedCount() {
    return results.stream()
        .filter(
            result -> result.status() != GateStatus.PASSED && result.status() != GateStatus.SKIPPED)
        .count();
  }

  public long skippedCount() {
    return results.stream().filter(result -> result.status() == GateStatus.SKIPPED).count();
  }

  public OptionalInt totalErrors() {
    return total(GateResult::errors);
  }

  public OptionalInt totalWarnings() {
    return total(GateResult::warnings);
  }

  private OptionalInt total(Function<GateResult, Integer> count) {
    List<GateResult> reported =
        results.stream().filter(result -> count.apply(result) != null).toList();
    return reported.isEmpty()
        ? OptionalInt.empty()
        : OptionalInt.of(reported.stream().mapToInt(count::apply).sum());
  }
}
