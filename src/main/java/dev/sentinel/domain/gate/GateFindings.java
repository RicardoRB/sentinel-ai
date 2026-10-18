package dev.sentinel.domain.gate;

import java.util.List;

/** All findings extracted for one gate. Renderers apply the presentation cap and threshold. */
public record GateFindings(List<Finding> items, int total) {
  public static final int LIMIT = 50;
  public static final GateFindings NONE = new GateFindings(List.of(), 0);

  public GateFindings {
    items = List.copyOf(items);
    if (total < items.size()) {
      throw new IllegalArgumentException("total must not be smaller than the kept findings");
    }
  }

  /** Keeps all findings in report order. */
  public static GateFindings of(final List<Finding> findings) {
    if (findings.isEmpty()) {
      return NONE;
    }
    return new GateFindings(findings, findings.size());
  }

  public Visible visible(final SeverityThreshold threshold) {
    final List<Finding> matching =
        items.stream().filter(f -> threshold.includes(f.severity())).toList();
    return new Visible(
        matching.subList(0, Math.min(LIMIT, matching.size())),
        total,
        total - matching.size(),
        matching.size() > LIMIT);
  }

  public boolean truncated() {
    return false;
  }

  public boolean isEmpty() {
    return total == 0;
  }

  public record Visible(List<Finding> items, int total, int hidden, boolean truncated) {
    public Visible {
      items = List.copyOf(items);
    }
  }
}
