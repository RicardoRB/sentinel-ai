package dev.sentinel.domain.gate;

/**
 * A tool report exists but cannot be read or parsed. Extraction callers treat it as "no findings";
 * messages must name at most the report file name, never its contents.
 */
public class FindingExtractionException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public FindingExtractionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
