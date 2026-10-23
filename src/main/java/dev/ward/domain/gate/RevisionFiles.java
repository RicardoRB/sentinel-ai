package dev.ward.domain.gate;

import java.nio.file.Path;
import java.util.Optional;

/** Outbound Git port for gates that compare a file with an earlier revision. */
public interface RevisionFiles {
  /** Returns the commit ID {@code revision} names in {@code root}, or empty when it names none. */
  Optional<String> resolve(Path root, String revision);

  /** Returns the merge base of {@code first} and {@code second}, or empty when there is none. */
  Optional<String> mergeBase(Path root, String first, String second);

  /** Returns whether {@code path}, relative to {@code root}, exists at commit {@code commit}. */
  boolean exists(Path root, String commit, String path);
}
