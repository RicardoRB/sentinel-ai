package dev.sentinel.domain.spec;

import java.nio.file.Path;
import java.util.Optional;

/** Outbound contract for resolving agent-reported paths against a project on the filesystem. */
public interface ProjectPaths {

  /**
   * Returns the normalized project-relative form of {@code path} when it stays inside {@code
   * projectRoot} after resolving {@code .}, {@code ..}, and symbolic links of existing ancestors.
   * Relative paths resolve against the root; anything outside it, or unparsable, is empty.
   */
  Optional<Path> containedRelative(Path projectRoot, String path);

  /** Returns whether the project-relative path names an existing directory. */
  boolean isDirectory(Path projectRoot, Path relative);

  /** Returns whether the project-relative path names an existing regular file. */
  boolean isRegularFile(Path projectRoot, Path relative);
}
