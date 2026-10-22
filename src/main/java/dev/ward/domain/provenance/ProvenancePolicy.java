package dev.ward.domain.provenance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Pure archive immutability and exact archive-backed live-spec association policy. */
public final class ProvenancePolicy {
  private static final String SPECS = "openspec/specs/";
  private static final String ARCHIVE = "openspec/changes/archive/";

  /**
   * Returns findings for archives that changed after their merge, and for live-spec changes that no
   * new archive's replay in {@code replays} explains.
   */
  public List<EnforcementFinding> evaluate(
      final GitRevisionSnapshot baseline,
      final GitRevisionSnapshot candidate,
      final List<GitChange> changes,
      final Map<String, GitRevisionSnapshot> replays) {
    final Set<String> baselineArchives = directories(baseline.files().keySet(), ARCHIVE);
    final Set<String> candidateArchives = directories(candidate.files().keySet(), ARCHIVE);
    final List<EnforcementFinding> findings = archiveFindings(changes, baselineArchives);
    final Set<String> liveChanges = liveChanges(changes);
    final Set<String> newArchives = new HashSet<>(candidateArchives);
    newArchives.removeAll(baselineArchives);
    findings.addAll(replayFindings(baseline, candidate, newArchives, replays));
    findings.addAll(associationFindings(liveChanges, newArchives));
    return findings.stream()
        .sorted(
            Comparator.comparing(EnforcementFinding::category)
                .thenComparing(EnforcementFinding::path))
        .toList();
  }

  private static List<EnforcementFinding> archiveFindings(
      final List<GitChange> changes, final Set<String> baselineArchives) {
    final List<EnforcementFinding> findings = new ArrayList<>();
    for (final GitChange change : changes) {
      if (baselineArchives.contains(archiveDirectory(change.oldPath()))
          || baselineArchives.contains(archiveDirectory(change.newPath()))) {
        findings.add(
            new EnforcementFinding(
                EnforcementFinding.Category.ARCHIVE_IMMUTABILITY,
                change.newPath().isBlank() ? change.oldPath() : change.newPath(),
                "Archived changes present at the merge base are immutable."));
      }
    }
    return findings;
  }

  private static Set<String> liveChanges(final List<GitChange> changes) {
    final Set<String> liveChanges = new TreeSet<>();
    for (final GitChange change : changes) {
      if (change.oldPath().startsWith(SPECS)) {
        liveChanges.add(change.oldPath());
      }
      if (change.newPath().startsWith(SPECS)) {
        liveChanges.add(change.newPath());
      }
    }
    return liveChanges;
  }

  private static List<EnforcementFinding> replayFindings(
      final GitRevisionSnapshot baseline,
      final GitRevisionSnapshot candidate,
      final Set<String> newArchives,
      final Map<String, GitRevisionSnapshot> replays) {
    final List<EnforcementFinding> findings = new ArrayList<>();
    final Set<String> actual = transition(baseline, candidate);
    for (final String archive : newArchives) {
      final GitRevisionSnapshot replay = replays.get(archive);
      final Set<String> replayed = transition(baseline, replay == null ? baseline : replay);
      if (replay == null || actual.isEmpty() || !replayed.equals(actual)) {
        findings.add(
            new EnforcementFinding(
                EnforcementFinding.Category.PROVENANCE,
                archive,
                "Archive replay does not exactly explain the candidate live-spec transition."));
      }
    }
    return findings;
  }

  private static List<EnforcementFinding> associationFindings(
      final Set<String> liveChanges, final Set<String> newArchives) {
    if (liveChanges.isEmpty()) {
      return List.of();
    }
    if (newArchives.isEmpty()) {
      return List.of(
          new EnforcementFinding(
              EnforcementFinding.Category.PROVENANCE,
              liveChanges.iterator().next(),
              "Every changed live specification must be explained by one newly added archive."));
    }
    if (newArchives.stream().skip(1).findAny().isPresent()) {
      return List.of(
          new EnforcementFinding(
              EnforcementFinding.Category.PROVENANCE,
              newArchives.iterator().next(),
              "A live-spec transition must have one exact archive explanation."));
    }
    return List.of();
  }

  private static Set<String> transition(
      final GitRevisionSnapshot baseline, final GitRevisionSnapshot candidate) {
    final Set<String> paths = new TreeSet<>();
    final Set<String> all = new HashSet<>(baseline.files().keySet());
    all.addAll(candidate.files().keySet());
    all.stream()
        .filter(path -> path.startsWith(SPECS))
        .filter(path -> !Objects.equals(baseline.files().get(path), candidate.files().get(path)))
        .forEach(paths::add);
    return paths;
  }

  private static Set<String> directories(final Set<String> files, final String prefix) {
    return files.stream()
        .filter(path -> path.startsWith(prefix))
        .map(ProvenancePolicy::archiveDirectory)
        .filter(path -> !path.isBlank())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String archiveDirectory(final String path) {
    if (path == null || !path.startsWith(ARCHIVE)) {
      return "";
    }
    final int end = path.indexOf('/', ARCHIVE.length());
    return end < 0 ? path : path.substring(0, end);
  }
}
