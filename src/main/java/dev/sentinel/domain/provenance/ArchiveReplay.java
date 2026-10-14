package dev.sentinel.domain.provenance;

import java.nio.file.Path;

/** Outbound port for replaying one archive in a disposable staging tree. */
@FunctionalInterface
public interface ArchiveReplay {
  GitRevisionSnapshot replay(Path repository, GitRevisionSnapshot baseline, String archivePath);
}
