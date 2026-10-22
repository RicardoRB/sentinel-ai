package dev.ward.domain.config;

/** An expected, user-facing failure (bad configuration, no project, ...). */
public class WardException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception with a message for the user. */
  public WardException(final String message) {
    super(message);
  }

  /** Creates the exception with a message for the user and its cause. */
  public WardException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
