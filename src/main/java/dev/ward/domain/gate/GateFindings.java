package dev.ward.domain.gate;

import java.util.List;

/** All findings extracted for one gate. Renderers apply the presentation cap and threshold. */
public record GateFindings(List<Finding> items, int total) {
  public static final int LIMIT = 50;
  public static final GateFindings NONE = new GateFindings(List.of(), 0);

  /**
   * Copies the findings.
   *
   * @throws IllegalArgumentException if {@code total} is smaller than the kept findings
   */
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

  /** Returns the findings at or above {@code threshold}, capped at {@link #LIMIT}. */
  public Visible visible(final SeverityThreshold threshold) {
    final List<Finding> matching =
        items.stream().filter(f -> threshold.includes(f.severity())).toList();
    return new Visible(
        matching.subList(0, Math.min(LIMIT, matching.size())),
        total,
        total - matching.size(),
        matching.size() > LIMIT);
  }

  /** Returns false: all extracted findings are kept, and only {@link #visible} caps them. */
  public boolean truncated() {
    return false;
  }

  /** Returns whether the gate reported no findings. */
  public boolean isEmpty() {
    return total == 0;
  }

  /**
   * The findings a report shows for one gate.
   *
   * @param items the shown findings
   * @param total findings at or above the threshold
   * @param hidden findings below the threshold
   * @param truncated whether {@code items} was capped
   */
  public record Visible(List<Finding> items, int total, int hidden, boolean truncated) {
    /** Copies the shown findings. */
    public Visible {
      items = List.copyOf(items);
    }
  }
}
