package dev.sentinel.domain.learning;

public class LearningStoreException extends RuntimeException {
  public LearningStoreException(String message, Throwable cause) {
    super(message, cause);
  }

  public LearningStoreException(String message) {
    super(message);
  }
}
