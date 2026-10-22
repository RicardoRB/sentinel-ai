package dev.ward.application.gate;

import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.ReportSink;
import dev.ward.domain.gate.ReportWriteException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Writes a rendered check report to a file requested with {@code --output}. */
public class CheckReportExport {
  private static final Logger LOGGER = Logger.getLogger(CheckReportExport.class.getName());

  private final ReportSink sink;

  /** Creates the export; {@code sink} persists the report file. */
  @Inject
  public CheckReportExport(final ReportSink sink) {
    this.sink = sink;
  }

  /** Writes the rendered report {@code content} to {@code target}. */
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
      throw new WardException("Cannot write report to " + target + ".", exception);
    }
  }
}
