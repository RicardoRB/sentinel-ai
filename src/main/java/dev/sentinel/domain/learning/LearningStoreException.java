package dev.sentinel.domain.learning;

public class LearningStoreException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public LearningStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public LearningStoreException(final String message) {
    super(message);
  }
}
