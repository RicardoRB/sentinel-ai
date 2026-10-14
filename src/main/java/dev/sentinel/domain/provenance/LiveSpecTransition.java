package dev.sentinel.domain.provenance;

import java.util.List;

/** The complete live-spec transition, independent of Git rename heuristics. */
public record LiveSpecTransition(List<String> changedPaths) {
  public LiveSpecTransition {
    changedPaths =
        changedPaths == null ? List.of() : List.copyOf(changedPaths.stream().sorted().toList());
  }
}
