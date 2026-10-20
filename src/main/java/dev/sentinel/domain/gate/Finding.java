package dev.sentinel.domain.gate;

import java.util.Objects;

/**
 * One violation reported by a quality-gate tool.
 *
 * <p>{@code file} is project-root-relative with {@code /} separators. Location parts and {@code
 * helpUri} are {@code null} when the tool does not report them.
 */
public record Finding(
    String gateId,
    String tool,
    String ruleId,
    FindingSeverity severity,
    String message,
    String file,
    Integer line,
    Integer column,
    String helpUri) {

  /**
   * Validates the required fields.
   *
   * @throws NullPointerException if the gate, tool, rule, severity, or message is missing
   */
  public Finding {
    Objects.requireNonNull(gateId, "gateId");
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    if (line != null && line < 1 || column != null && column < 1) {
      throw new IllegalArgumentException("line and column are 1-based");
    }
  }

  /** Returns whether the finding points at a file. */
  public boolean located() {
    return file != null;
  }
}
