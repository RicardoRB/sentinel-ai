package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.config.SentinelException;

/** An answer that can be corrected by asking again, unlike input that ended. */
final class InvalidAnswerException extends SentinelException {
  private static final long serialVersionUID = 1L;

  InvalidAnswerException(final String message) {
    super(message);
  }

  InvalidAnswerException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
