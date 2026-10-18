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

  public String id() {
    return id;
  }

  public boolean includes(final FindingSeverity severity) {
    return switch (this) {
      case ERROR -> severity == FindingSeverity.ERROR;
      case WARNING -> severity == FindingSeverity.ERROR || severity == FindingSeverity.WARNING;
      case INFO -> true;
    };
  }

  public boolean showsCapturedOutput() {
    return this == INFO;
  }

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
