package dev.sentinel.domain.traceability;

import java.util.List;

/**
 * Entries read from a project with the sources that could not be read.
 *
 * @param entries what was found
 * @param failures descriptions of unreadable sources
 * @param <T> the entry type
 */
public record TraceabilityInventory<T>(List<T> entries, List<String> failures) {
  /** Copies the lists. */
  public TraceabilityInventory {
    entries = List.copyOf(entries);
    failures = List.copyOf(failures);
  }
}
