package dev.sentinel.domain.gate;

import java.util.Locale;

/** Minimum finding severity included in a rendered check report. */
public enum SeverityThreshold {
  ERROR("error"),
  WARNING("warning"),
  INFO("info");

  private final String id;

  SeverityThreshold(final String id) {
    this.id = id;
  }

  /** Returns the value used on the command line. */
  public String id() {
    return id;
  }

  /** Returns whether findings of {@code severity} are shown at this threshold. */
  public boolean includes(final FindingSeverity severity) {
    return switch (this) {
      case ERROR -> severity == FindingSeverity.ERROR;
      case WARNING -> severity == FindingSeverity.ERROR || severity == FindingSeverity.WARNING;
      case INFO -> true;
    };
  }

  /** Returns whether captured gate output is shown in full. */
  public boolean showsCapturedOutput() {
    return this == INFO;
  }

  /**
   * Parses a {@code --severity} value, ignoring case.
   *
   * @throws IllegalArgumentException if it is not error, warning, or info
   */
  public static SeverityThreshold parse(final String value) {
    final String normalized = value.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "error" -> ERROR;
      case "warning" -> WARNING;
      case "info" -> INFO;
      default -> throw new IllegalArgumentException("expected error, warning or info");
    };
  }
}
