package dev.sentinel.domain.gate;

/** Determines which captured output, if any, belongs in a rendered gate report. */
public enum GateOutputVisibility {
  NONE,
  FINDINGS,
  TAIL,
  FULL;

  /**
   * Returns what to show for {@code result}: everything at info level, nothing for a passed gate,
   * findings when some are visible, and otherwise the output tail.
   */
  public static GateOutputVisibility of(
      final GateResult result, final SeverityThreshold threshold) {
    if (threshold.showsCapturedOutput()) {
      return FULL;
    }
    if (result.passed()) {
      return NONE;
    }
    return result.findings().visible(threshold).items().isEmpty() ? TAIL : FINDINGS;
  }
}
