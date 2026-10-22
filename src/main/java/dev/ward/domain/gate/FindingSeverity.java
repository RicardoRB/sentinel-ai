package dev.ward.domain.gate;

import java.util.Locale;

/** Severity of a single finding, aligned with SARIF result levels. */
public enum FindingSeverity {
  ERROR,
  WARNING,
  NOTE;

  /** Returns the lowercase label used in JSON and SARIF output. */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }
}
