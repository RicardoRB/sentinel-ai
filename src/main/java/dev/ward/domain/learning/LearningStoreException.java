package dev.ward.domain.learning;

/** Reports that the learning store could not be read or written. */
public class LearningStoreException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception with its cause. */
  public LearningStoreException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /** Creates the exception. */
  public LearningStoreException(final String message) {
    super(message);
  }
}
