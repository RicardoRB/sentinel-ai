package dev.sentinel.domain.gate;

import java.util.List;

/** The findings kept for one gate: at most {@link #LIMIT} items plus the reported total. */
public record GateFindings(List<Finding> items, int total) {
  public static final int LIMIT = 50;
  public static final GateFindings NONE = new GateFindings(List.of(), 0);

  public GateFindings {
    items = List.copyOf(items);
    if (total < items.size()) {
      throw new IllegalArgumentException("total must not be smaller than the kept findings");
    }
  }

  /** Keeps the first {@link #LIMIT} findings in report order and records the full count. */
  public static GateFindings of(final List<Finding> findings) {
    if (findings.isEmpty()) {
      return NONE;
    }
    return new GateFindings(findings.subList(0, Math.min(LIMIT, findings.size())), findings.size());
  }

  public boolean truncated() {
    return total > items.size();
  }

  public boolean isEmpty() {
    return total == 0;
  }
}
