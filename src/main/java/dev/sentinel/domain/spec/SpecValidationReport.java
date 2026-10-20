package dev.sentinel.domain.spec;

import java.util.List;

/** Outcome of one spec validation request; an empty report means there was nothing to report. */
public record SpecValidationReport(List<SpecItemReport> items) {

  public static final SpecValidationReport EMPTY = new SpecValidationReport(List.of());

  /** Copies the items. */
  public SpecValidationReport {
    items = List.copyOf(items);
  }

  /** Returns whether any item has a finding. */
  public boolean hasFindings() {
    return items.stream().anyMatch(SpecItemReport::hasFindings);
  }
}
