package dev.sentinel.domain.spec;

import java.util.Objects;

/** One validation finding; {@code location} is {@code null} when the validator names none. */
public record SpecFinding(Level level, String location, String message) {

  /** Finding severity, declared from most to least severe. */
  public enum Level {
    ERROR,
    WARNING,
    INFO
  }

  /**
   * Validates the level; a missing message means empty.
   *
   * @throws NullPointerException if {@code level} is missing
   */
  public SpecFinding {
    Objects.requireNonNull(level, "level");
    message = message == null ? "" : message;
  }
}
