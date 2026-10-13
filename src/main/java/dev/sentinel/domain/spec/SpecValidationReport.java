package dev.sentinel.domain.spec;

import java.util.List;

/** Outcome of one spec validation request; an empty report means there was nothing to report. */
public record SpecValidationReport(List<SpecItemReport> items) {

  public static final SpecValidationReport EMPTY = new SpecValidationReport(List.of());

  public SpecValidationReport {
    items = List.copyOf(items);
  }

  public boolean hasFindings() {
    return items.stream().anyMatch(SpecItemReport::hasFindings);
  }
}
