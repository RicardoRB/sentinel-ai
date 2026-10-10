package dev.sentinel.domain.loop;

public record LoopConfiguration(int maxIterations, int timeoutSeconds, boolean allowDirty) {
  public LoopConfiguration {
    if (maxIterations <= 0) {
      throw new IllegalArgumentException("maxIterations must be positive");
    }
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
  }
}
