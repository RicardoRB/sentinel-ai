package dev.sentinel.domain.provenance;

import java.nio.file.Path;
import java.util.List;

/** Outbound Git port. Implementations own process execution and parsing. */
public interface GitRepository {
  String head(Path repository);

  String resolveMergeBase(Path repository, String base, String head);

  GitRevisionSnapshot snapshot(Path repository, String revision);

  List<GitChange> changes(Path repository, String base, String head);
}
