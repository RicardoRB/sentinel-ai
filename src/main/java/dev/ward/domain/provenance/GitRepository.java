package dev.ward.domain.provenance;

import java.nio.file.Path;
import java.util.List;

/** Outbound Git port. Implementations own process execution and parsing. */
public interface GitRepository {
  /** Returns the commit ID of HEAD in {@code repository}. */
  String head(Path repository);

  /** Returns the merge base of {@code base} and {@code head}. */
  String resolveMergeBase(Path repository, String base, String head);

  /** Returns the OpenSpec files at {@code revision}. */
  GitRevisionSnapshot snapshot(Path repository, String revision);

  /** Returns the OpenSpec changes between {@code base} and {@code head}, with renames detected. */
  List<GitChange> changes(Path repository, String base, String head);
}
