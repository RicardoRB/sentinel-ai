package dev.sentinel.domain.gate;

import java.util.Locale;

/** How a gate was executed during a partial check. */
public enum GateScope {
  SKIPPED,
  NARROWED,
  FULL;

  /** The lower-case name reports use. */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }
}
