package dev.sentinel.infrastructure.report;

import dev.sentinel.domain.gate.ReportSink;
import dev.sentinel.domain.gate.ReportWriteException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;

/** Writes check reports to the local file system as UTF-8. */
public final class FileReportSink implements ReportSink {
  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileReportSink() {}

  @Override
  public void persist(final Path target, final String content) {
    final Path absolute = target.toAbsolutePath().normalize();
    try {
      final Path parent = absolute.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(absolute, content, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new ReportWriteException("Cannot write " + absolute.getFileName(), exception);
    }
  }
}
