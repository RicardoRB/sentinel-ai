package dev.sentinel.domain.spec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether an operation on a project-relative path would modify approved OpenSpec artifacts.
 * Agents may change active changes freely, but approved specs and archived changes are read-only.
 */
public final class ApprovedArtifactPolicy {
  /** Approved specs, relative to the project root. */
  public static final Path SPECS_ROOT = Path.of(OpenSpecLayout.ROOT, OpenSpecLayout.SPECS);

  /** Archived changes, relative to the project root. */
  public static final Path ARCHIVE_ROOT =
      Path.of(OpenSpecLayout.ROOT, OpenSpecLayout.CHANGES, OpenSpecLayout.ARCHIVE);

  /** Every protected area root, relative to the project root. */
  public static final List<Path> PROTECTED_ROOTS = List.of(SPECS_ROOT, ARCHIVE_ROOT);

  private static final String PARENT = "..";
  private static final String EXTENSION = ".";

  private ApprovedArtifactPolicy() {}

  /**
   * Returns the protected area an operation on {@code relative} would modify. Paths that escape the
   * project or are absolute are not this policy's concern and are allowed.
   */
  public static Optional<ProtectedArea> evaluate(
      final Path relative, final GuardOperation operation) {
    if (relative == null || relative.isAbsolute()) {
      return Optional.empty();
    }
    final Path normalized = relative.normalize();
    if (normalized.startsWith(PARENT)) {
      return Optional.empty();
    }
    if (normalized.startsWith(SPECS_ROOT)) {
      return Optional.of(ProtectedArea.SPECS);
    }
    if (normalized.startsWith(ARCHIVE_ROOT)) {
      return Optional.of(ProtectedArea.ARCHIVE);
    }
    if (operation.removesTree()
        && PROTECTED_ROOTS.stream().anyMatch(root -> isAncestor(normalized, root))) {
      return Optional.of(ProtectedArea.ANCESTOR);
    }
    return Optional.empty();
  }

  /**
   * Returns where an agent records a change to the approved spec at {@code relative}, using the
   * literal {@code <change>} placeholder for the active change.
   */
  public static String deltaPath(final Path relative) {
    final List<String> capability = new ArrayList<>();
    final Path normalized = relative.normalize();
    if (normalized.startsWith(SPECS_ROOT)) {
      normalized
          .subpath(0, normalized.getNameCount())
          .forEach(part -> capability.add(part.toString()));
      capability.subList(0, SPECS_ROOT.getNameCount()).clear();
    }
    if (!capability.isEmpty() && capability.getLast().contains(EXTENSION)) {
      capability.removeLast();
    }
    final String name = capability.isEmpty() ? "<capability-path>" : String.join("/", capability);
    return String.join(
        "/",
        OpenSpecLayout.ROOT,
        OpenSpecLayout.CHANGES,
        "<change>",
        OpenSpecLayout.SPECS,
        name,
        OpenSpecLayout.SPEC_FILE);
  }

  private static boolean isAncestor(final Path candidate, final Path root) {
    return candidate.toString().isEmpty() || root.startsWith(candidate);
  }
}
