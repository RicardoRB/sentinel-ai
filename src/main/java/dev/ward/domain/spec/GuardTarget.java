package dev.ward.domain.spec;

import java.util.Objects;

/**
 * A path an agent tool call would affect, exactly as the agent named it.
 *
 * @param glob whether the path is an unquoted shell pattern rather than a literal path
 */
public record GuardTarget(GuardOperation operation, String path, boolean glob) {

  /**
   * Validates the target.
   *
   * @throws NullPointerException if the operation or path is missing
   */
  public GuardTarget {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(path, "path");
  }

  /** Creates a target for a literal path. */
  public static GuardTarget of(final GuardOperation operation, final String path) {
    return new GuardTarget(operation, path, false);
  }
}
