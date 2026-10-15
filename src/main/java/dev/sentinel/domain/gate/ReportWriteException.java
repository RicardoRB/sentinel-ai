package dev.sentinel.domain.gate;

/** A report file or its directory could not be written. */
public class ReportWriteException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ReportWriteException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
