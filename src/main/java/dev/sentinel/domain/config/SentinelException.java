package dev.sentinel.domain.config;

/** An expected, user-facing failure (bad configuration, no project, ...). */
public class SentinelException extends RuntimeException {

  public SentinelException(final String message) {
    super(message);
  }

  public SentinelException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
