package dev.sentinel.domain.gate;

import java.nio.file.Path;

/** Port that prepares directories gate tools expect to write reports into. */
@FunctionalInterface
public interface ReportDirectories {
  ReportDirectories NONE = (projectRoot, reportPath) -> {};

  /**
   * Creates the parent directory of {@code reportPath} when it resolves inside {@code projectRoot}.
   * Failures raise {@link ReportWriteException}.
   */
  void prepareParent(Path projectRoot, String reportPath);
}
