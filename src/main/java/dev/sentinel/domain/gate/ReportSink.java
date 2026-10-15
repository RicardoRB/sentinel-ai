package dev.sentinel.domain.gate;

import java.nio.file.Path;

/** Port that persists a rendered machine-readable check report. */
@FunctionalInterface
public interface ReportSink {
  /**
   * Writes {@code content} to {@code target}, creating missing parent directories. Failures raise
   * {@link ReportWriteException}.
   */
  void persist(Path target, String content);
}
