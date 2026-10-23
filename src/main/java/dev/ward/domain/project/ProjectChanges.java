package dev.ward.domain.project;

import java.nio.file.Path;

/** Outbound contract for asking version control whether files below a directory changed. */
@FunctionalInterface
public interface ProjectChanges {
  /**
   * Returns whether a file below {@code directory} differs from the last commit in the working tree
   * or the index, or is new; true when version control cannot tell.
   */
  boolean changedBelow(Path directory);
}
