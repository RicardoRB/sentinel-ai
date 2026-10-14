package dev.sentinel.application.spec;

import dev.sentinel.domain.spec.ApprovedArtifactPolicy;
import dev.sentinel.domain.spec.GlobPattern;
import dev.sentinel.domain.spec.GuardOperation;
import dev.sentinel.domain.spec.GuardRefusal;
import dev.sentinel.domain.spec.GuardTarget;
import dev.sentinel.domain.spec.ProjectPaths;
import dev.sentinel.domain.spec.ProtectedArea;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/** Resolves agent-named targets, including shell patterns, to protected-area refusals. */
final class GuardTargetResolver {
  private static final List<Path> PROTECTED_ROOTS = ApprovedArtifactPolicy.PROTECTED_ROOTS;
  private static final String CURRENT = ".";
  private static final String SEPARATOR = "/";

  private final ProjectPaths paths;
  private final Path root;
  private final String workingDirectory;

  GuardTargetResolver(final ProjectPaths paths, final Path root, final String workingDirectory) {
    this.paths = paths;
    this.root = root;
    this.workingDirectory = workingDirectory;
  }

  /** Returns the refusal for a target, or empty when it leaves protected artifacts unchanged. */
  Optional<GuardRefusal> refusal(final GuardTarget target) {
    if (!target.glob()) {
      return literal(target.operation(), target.path());
    }
    return pattern(target);
  }

  private Optional<GuardRefusal> literal(final GuardOperation operation, final String path) {
    return paths.guardCandidates(root, workingDirectory, path, PROTECTED_ROOTS).stream()
        .flatMap(
            candidate ->
                ApprovedArtifactPolicy.evaluate(candidate, operation)
                    .map(area -> GuardRefusal.forPath(area, candidate))
                    .stream())
        .findFirst();
  }

  /**
   * Refuses a pattern whose literal directory is protected, or whose remaining segments could match
   * a protected root or an ancestor the operation would remove.
   */
  private Optional<GuardRefusal> pattern(final GuardTarget target) {
    final String path = target.path();
    final int slash = path.lastIndexOf(SEPARATOR, GlobPattern.firstPatternCharacter(path));
    final String prefix = slash < 0 ? CURRENT : slash == 0 ? SEPARATOR : path.substring(0, slash);
    final Optional<GuardRefusal> inside = literal(GuardOperation.WRITE, prefix);
    if (inside.isPresent()) {
      return inside;
    }
    final List<String> segments =
        List.of(path.substring(slash + 1).split(SEPARATOR)).stream()
            .filter(segment -> !segment.isEmpty())
            .toList();
    for (final Path candidate :
        paths.guardCandidates(root, workingDirectory, prefix, PROTECTED_ROOTS)) {
      for (final Path protectedRoot : PROTECTED_ROOTS) {
        final Optional<GuardRefusal> refusal =
            matched(candidate, protectedRoot, segments, target.operation());
        if (refusal.isPresent()) {
          return refusal;
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<GuardRefusal> matched(
      final Path directory,
      final Path protectedRoot,
      final List<String> pattern,
      final GuardOperation operation) {
    final boolean projectRoot = directory.toString().isEmpty();
    if (!projectRoot && !protectedRoot.startsWith(directory)) {
      return Optional.empty();
    }
    final List<String> below =
        names(projectRoot ? protectedRoot : directory.relativize(protectedRoot));
    final int reached = GlobPattern.reach(pattern, below);
    if (reached <= 0) {
      return Optional.empty();
    }
    final Path match = directory.resolve(String.join(SEPARATOR, below.subList(0, reached)));
    final GuardOperation effective = reached == below.size() ? GuardOperation.WRITE : operation;
    final Optional<ProtectedArea> area = ApprovedArtifactPolicy.evaluate(match, effective);
    return area.map(found -> GuardRefusal.forPath(found, match));
  }

  private static List<String> names(final Path path) {
    final List<String> names = new ArrayList<>();
    StreamSupport.stream(path.spliterator(), false).forEach(name -> names.add(name.toString()));
    return names;
  }
}
