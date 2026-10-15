package dev.sentinel.application.gate;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.ReportSink;
import dev.sentinel.domain.gate.ReportWriteException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Writes a rendered check report to a file requested with {@code --output}. */
public class CheckReportExport {
  private static final Logger LOGGER = Logger.getLogger(CheckReportExport.class.getName());

  private final ReportSink sink;

  @Inject
  public CheckReportExport(final ReportSink sink) {
    this.sink = sink;
  }

  public void export(final Path target, final String content) {
    try {
      sink.persist(target, content);
      LOGGER.log(
          Level.INFO,
          () -> "event=report-written file=" + target.getFileName() + " bytes=" + content.length());
    } catch (ReportWriteException exception) {
      LOGGER.log(
          Level.WARNING,
          () -> "event=report-write-failed error=" + exception.getClass().getSimpleName());
      throw new SentinelException("Cannot write report to " + target + ".", exception);
    }
  }
}
