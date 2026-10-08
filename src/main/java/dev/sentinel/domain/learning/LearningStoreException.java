package dev.sentinel.domain.learning;

public class LearningStoreException extends RuntimeException {
  public LearningStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public LearningStoreException(final String message) {
    super(message);
  }
}
