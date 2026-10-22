package dev.ward.application.provenance;

import dev.ward.application.gate.CheckService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.provenance.ArchiveReplay;
import dev.ward.domain.provenance.BootstrapDeclaration;
import dev.ward.domain.provenance.BootstrapPolicy;
import dev.ward.domain.provenance.EnforcementFinding;
import dev.ward.domain.provenance.EnforcementReport;
import dev.ward.domain.provenance.GitChange;
import dev.ward.domain.provenance.GitRepository;
import dev.ward.domain.provenance.GitRevisionSnapshot;
import dev.ward.domain.provenance.ProvenancePolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Orchestrates existing configured checks with revision and provenance enforcement. */
public final class SpecEnforcementService {
  private static final Logger LOGGER = Logger.getLogger(SpecEnforcementService.class.getName());
  private final ProjectDetector detector;
  private final GitRepository git;
  private final ArchiveReplay replay;
  private final CheckService checks;
  private final ProvenancePolicy provenance = new ProvenancePolicy();
  private final BootstrapPolicy bootstrap = new BootstrapPolicy();

  /**
   * Creates the service. {@code git} reads revisions, {@code replay} replays archived changes onto
   * the baseline, and {@code checks} runs the configured quality gates.
   */
  @Inject
  public SpecEnforcementService(
      final ProjectDetector detector,
      final GitRepository git,
      final ArchiveReplay replay,
      final CheckService checks) {
    this.detector = detector;
    this.git = git;
    this.replay = replay;
    this.checks = checks;
  }

  /**
   * Enforces OpenSpec provenance between the merge base of {@code base} and HEAD, then runs the
   * configured checks of {@code profiles}.
   */
  public EnforcementReport enforce(
      final Path start,
      final String base,
      final List<String> profiles,
      final BootstrapDeclaration declaration,
      final boolean bootstrapSelected) {
    final Path root = detector.detect(start).orElseThrow(ProjectNotFoundException::new).root();
    final List<EnforcementFinding> findings = new ArrayList<>();
    final String head;
    final String mergeBase;
    final GitRevisionSnapshot baseline;
    final GitRevisionSnapshot candidate;
    final List<GitChange> changes;
    try {
      LOGGER.log(Level.INFO, "event=spec-enforce-revision-resolution");
      head = git.head(root);
      mergeBase = git.resolveMergeBase(root, base, head);
      baseline = git.snapshot(root, mergeBase);
      candidate = git.snapshot(root, head);
      changes = git.changes(root, mergeBase, head);
    } catch (IllegalStateException exception) {
      findings.add(
          new EnforcementFinding(
              EnforcementFinding.Category.PREREQUISITE,
              "git",
              "Could not resolve repository, base revision, or merge-base history; fetch the base and sufficient history."));
      return new EnforcementReport(findings, 2);
    }

    final Optional<EnforcementFinding> bootstrapFinding =
        bootstrap.check(baseline, candidate, declaration, bootstrapSelected);
    bootstrapFinding.ifPresent(findings::add);
    if (bootstrapFinding.isEmpty() || bootstrapSelected) {
      addConfiguredFindings(findings, root, profiles);
      addTaskFindings(findings, candidate, baseline);
      if (bootstrapFinding.isEmpty()) {
        final Map<String, GitRevisionSnapshot> replays = new HashMap<>();
        for (final String archive : newArchives(baseline, candidate)) {
          try {
            LOGGER.log(Level.INFO, () -> "event=spec-enforce-replay archive=" + archive);
            replays.put(archive, replay.replay(root, baseline, archive));
          } catch (IllegalStateException exception) {
            findings.add(
                new EnforcementFinding(
                    EnforcementFinding.Category.PROVENANCE,
                    archive,
                    "Could not replay the archived change in an isolated staging tree."));
          }
        }
        findings.addAll(provenance.evaluate(baseline, candidate, changes, replays));
      }
    }
    LOGGER.log(Level.INFO, () -> "event=spec-enforce-decision findings=" + findings.size());
    return new EnforcementReport(findings, findings.isEmpty() ? 0 : 1);
  }

  private void addConfiguredFindings(
      final List<EnforcementFinding> findings, final Path root, final List<String> profiles) {
    final CheckReport report =
        checks.check(
            root, profiles, Set.of("openspec", "openspec-archived", "openspec-junit-traceability"));
    for (final GateResult result : report.results()) {
      if (!result.passed()) {
        findings.add(
            new EnforcementFinding(
                EnforcementFinding.Category.CONFIGURED_CHECK,
                result.name(),
                result.summary() == null || result.summary().isBlank()
                    ? "Configured OpenSpec check failed."
                    : result.summary()));
      }
    }
  }

  private static void addTaskFindings(
      final List<EnforcementFinding> findings,
      final GitRevisionSnapshot candidate,
      final GitRevisionSnapshot baseline) {
    final Set<String> baselineArchives = archiveDirectories(baseline);
    for (final String archive : archiveDirectories(candidate)) {
      if (baselineArchives.contains(archive)) {
        continue;
      }
      candidate.files().entrySet().stream()
          .filter(entry -> entry.getKey().startsWith(archive + "/"))
          .flatMap(entry -> Arrays.stream(entry.getValue().split("\\R")))
          .filter(line -> line.trim().startsWith("- [ ]"))
          .forEach(
              line ->
                  findings.add(
                      new EnforcementFinding(
                          EnforcementFinding.Category.ARCHIVE_TASK,
                          archive,
                          "Archive contains an unchecked task: " + line.trim())));
    }
  }

  private static Set<String> newArchives(
      final GitRevisionSnapshot baseline, final GitRevisionSnapshot candidate) {
    final Set<String> result = archiveDirectories(candidate);
    result.removeAll(archiveDirectories(baseline));
    return result;
  }

  private static Set<String> archiveDirectories(final GitRevisionSnapshot snapshot) {
    final Set<String> result = new HashSet<>();
    for (final String path : snapshot.files().keySet()) {
      final String prefix = "openspec/changes/archive/";
      if (!path.startsWith(prefix)) {
        continue;
      }
      final int end = path.indexOf('/', prefix.length());
      if (end > 0) {
        result.add(path.substring(0, end));
      }
    }
    return result;
  }
}
