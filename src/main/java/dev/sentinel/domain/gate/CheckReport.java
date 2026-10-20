package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

/** The results of one check run for a project, with the edited paths of a partial check. */
public record CheckReport(Project project, List<GateResult> results, List<String> partialPaths) {

  /** Creates the report of a full check. */
  public CheckReport(final Project project, final List<GateResult> results) {
    this(project, results, List.of());
  }

  /** Copies the lists. */
  public CheckReport {
    results = List.copyOf(results);
    partialPaths = List.copyOf(partialPaths);
  }

  /** Returns a copy carrying {@code replacement} results. */
  public CheckReport withResults(final List<GateResult> replacement) {
    return new CheckReport(project, replacement, partialPaths);
  }

  /** Returns a copy that records the edited {@code paths} of a partial check. */
  public CheckReport withPartialPaths(final List<String> paths) {
    return new CheckReport(project, results, paths);
  }

  /**
   * Returns PASSED when every gate that ran passed, otherwise FAILED; skipped gates do not count.
   */
  public GateStatus status() {
    return results.stream()
            .filter(result -> result.status() != GateStatus.SKIPPED)
            .allMatch(GateResult::passed)
        ? GateStatus.PASSED
        : GateStatus.FAILED;
  }

  /** Returns whether the overall status is PASSED. */
  public boolean passed() {
    return status() == GateStatus.PASSED;
  }

  /** Returns how many gates passed. */
  public long passedCount() {
    return results.stream().filter(result -> result.status() == GateStatus.PASSED).count();
  }

  /** Returns how many gates ran and did not pass. */
  public long failedCount() {
    return results.stream()
        .filter(
            result -> result.status() != GateStatus.PASSED && result.status() != GateStatus.SKIPPED)
        .count();
  }

  /** Returns how many gates were skipped. */
  public long skippedCount() {
    return results.stream().filter(result -> result.status() == GateStatus.SKIPPED).count();
  }

  /** Returns the sum of reported error counts, or empty when no gate reported one. */
  public OptionalInt totalErrors() {
    return total(GateResult::errors);
  }

  /** Returns the sum of reported warning counts, or empty when no gate reported one. */
  public OptionalInt totalWarnings() {
    return total(GateResult::warnings);
  }

  private OptionalInt total(final Function<GateResult, Integer> count) {
    final List<GateResult> reported =
        results.stream().filter(result -> count.apply(result) != null).toList();
    return reported.isEmpty()
        ? OptionalInt.empty()
        : OptionalInt.of(reported.stream().mapToInt(count::apply).sum());
  }
}
