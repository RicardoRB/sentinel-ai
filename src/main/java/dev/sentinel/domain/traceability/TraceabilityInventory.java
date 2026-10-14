package dev.sentinel.domain.traceability;

import java.util.List;

public record TraceabilityInventory<T>(List<T> entries, List<String> failures) {
  public TraceabilityInventory {
    entries = List.copyOf(entries);
    failures = List.copyOf(failures);
  }
}
