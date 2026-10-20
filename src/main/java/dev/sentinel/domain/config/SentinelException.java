package dev.sentinel.domain.config;

/** An expected, user-facing failure (bad configuration, no project, ...). */
public class SentinelException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with a message for the user. */
  public SentinelException(final String message) {
    super(message);
  }

  /** Creates the exception with a message for the user and its cause. */
  public SentinelException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
