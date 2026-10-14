package dev.sentinel.domain.spec;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

  /**
   * Returns every project-relative form through which {@code path} reaches the project: its lexical
   * form, its real-path form, and, for each existing protected root, the form found by following
   * symbolic links or comparing file identity. File identity catches aliases such as case variants
   * on case-insensitive file systems and protected roots that are themselves symbolic links. When
   * the path contains a protected root, the candidate is the corresponding ancestor of that root.
   *
   * @param workingDirectory the directory relative paths start from, relative to the root or
   *     absolute; blank for the project root
   * @return the candidate paths in discovery order; empty when the path is unrelated or unparsable
   */
  Set<Path> guardCandidates(
      Path projectRoot, String workingDirectory, String path, List<Path> protectedRoots);

  /** Returns the sorted names of active OpenSpec change directories. */
  List<String> activeChanges(Path projectRoot);
}
