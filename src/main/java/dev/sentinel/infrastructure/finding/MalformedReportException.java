package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.FindingExtractionException;
import java.nio.file.Path;

/** A tool report exists but cannot be parsed. The message names only the report file name. */
final class MalformedReportException extends FindingExtractionException {
  private static final long serialVersionUID = 1L;

  MalformedReportException(final Path report, final Throwable cause) {
    super("Malformed report " + report.getFileName(), cause);
  }
}
