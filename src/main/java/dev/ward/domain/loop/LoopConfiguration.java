package dev.ward.domain.loop;

/**
 * Limits for one quality-loop run.
 *
 * @param maxIterations the most agent attempts
 * @param timeoutSeconds how long each agent attempt may run
 * @param allowDirty whether the loop may start with uncommitted changes
 */
public record LoopConfiguration(int maxIterations, int timeoutSeconds, boolean allowDirty) {
  /**
   * Validates the limits.
   *
   * @throws IllegalArgumentException if the iterations or timeout are not positive
   */
  public LoopConfiguration {
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be positive");
    }
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
  }
}
