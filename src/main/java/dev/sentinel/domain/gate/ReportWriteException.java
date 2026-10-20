package dev.sentinel.domain.gate;

/** A report file or its directory could not be written. */
public class ReportWriteException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates the exception for a report file that could not be written. */
  public ReportWriteException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
