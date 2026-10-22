package dev.ward.domain.provenance;

import java.nio.file.Path;

/** Outbound port for replaying one archive in a disposable staging tree. */
@FunctionalInterface
public interface ArchiveReplay {
  /**
   * Applies the archive at {@code archivePath} to {@code baseline} in a staging tree and returns
   * the resulting live specs.
   */
  GitRevisionSnapshot replay(Path repository, GitRevisionSnapshot baseline, String archivePath);
}
