package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.ReportDirectories;
import dev.sentinel.domain.gate.ReportWriteException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Creates report parent directories inside the project root. */
public final class FileSystemReportDirectories implements ReportDirectories {
  private static final Logger LOGGER =
      Logger.getLogger(FileSystemReportDirectories.class.getName());

  @Inject
  public FileSystemReportDirectories() {}

  @Override
  public void prepareParent(final Path projectRoot, final String reportPath) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path parent;
    try {
      parent = root.resolve(reportPath).normalize().getParent();
    } catch (InvalidPathException exception) {
      LOGGER.log(Level.INFO, "event=report-directory-skipped reason=invalid-path");
      return;
    }
    if (parent == null || !parent.startsWith(root) || parent.equals(root)) {
      LOGGER.log(Level.INFO, "event=report-directory-skipped reason=outside-project");
      return;
    }
    if (Files.isDirectory(parent)) {
      return;
    }
    try {
      Files.createDirectories(parent);
      LOGGER.log(Level.INFO, () -> "event=report-directory-created dir=" + root.relativize(parent));
    } catch (IOException exception) {
      throw new ReportWriteException("Cannot create report directory", exception);
    }
  }
}
