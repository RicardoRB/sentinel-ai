package dev.sentinel.application.spec;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.spec.GuardDecision;
import dev.sentinel.domain.spec.GuardFeedback;
import dev.sentinel.domain.spec.GuardOperation;
import dev.sentinel.domain.spec.GuardRefusal;
import dev.sentinel.domain.spec.GuardRequest;
import dev.sentinel.domain.spec.GuardTarget;
import dev.sentinel.domain.spec.PatchTargets;
import dev.sentinel.domain.spec.ProjectPaths;
import dev.sentinel.domain.spec.ShellCommandHeuristic;
import dev.sentinel.domain.spec.ShellInspection;
import dev.sentinel.domain.spec.SkillPolicy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Decides whether an agent tool call may run without modifying approved OpenSpec specs or archived
 * changes. It reads no configuration and launches no process.
 */
public final class ApprovedArtifactGuardService {
  private static final Logger LOGGER =
      Logger.getLogger(ApprovedArtifactGuardService.class.getName());
  private static final Path OPENSPEC = Path.of("openspec");

  private final ProjectDetector detector;
  private final ProjectPaths paths;

  /** Creates the service; {@code paths} resolves and inspects project paths. */
  @Inject
  public ApprovedArtifactGuardService(final ProjectDetector detector, final ProjectPaths paths) {
    this.detector = detector;
    this.paths = paths;
  }

  /**
   * Decides whether the tool call in {@code request} may change approved specs or archived changes.
   */
  public GuardDecision evaluate(final GuardRequest request) {
    final long started = System.nanoTime();
    final Project project =
        detector.detect(request.start()).orElseThrow(ProjectNotFoundException::new);
    if (!paths.isDirectory(project.root(), OPENSPEC)) {
      LOGGER.log(
          Level.INFO,
          () -> "event=spec-guard-skipped kind=" + kind(request) + " reason=no-openspec");
      return GuardDecision.ALLOWED;
    }
    final Evaluation evaluation = evaluate(project.root(), request);
    LOGGER.log(Level.INFO, () -> describe(request, evaluation, started));
    return evaluation.refusals().isEmpty()
        ? GuardDecision.ALLOWED
        : GuardDecision.refused(
            GuardFeedback.render(evaluation.refusals(), paths.activeChanges(project.root())));
  }

  private Evaluation evaluate(final Path root, final GuardRequest request) {
    final String text = request.text();
    return switch (request.kind()) {
      case PATHS ->
          targets(
              root,
              request,
              request.values().stream()
                  .map(path -> GuardTarget.of(GuardOperation.WRITE, path))
                  .toList());
      case PATCH ->
          PatchTargets.parse(text)
              .map(targets -> targets(root, request, targets))
              .orElseGet(() -> uninspectable("patch"));
      case SHELL -> shell(root, request, ShellCommandHeuristic.inspect(text));
      case SKILL ->
          new Evaluation(
              0,
              SkillPolicy.archivesOrSyncs(text)
                  ? List.of(GuardRefusal.of(GuardRefusal.Reason.ARCHIVE_OR_SYNC_SKILL, text))
                  : List.of());
      case UNREADABLE -> uninspectable(text);
    };
  }

  private Evaluation shell(
      final Path root, final GuardRequest request, final ShellInspection inspection) {
    if (!inspection.inspectable()) {
      return uninspectable("shell command");
    }
    final Evaluation targets = targets(root, request, inspection.targets());
    if (!inspection.archiveCommand()) {
      return targets;
    }
    final List<GuardRefusal> refusals = new ArrayList<>();
    refusals.add(GuardRefusal.of(GuardRefusal.Reason.ARCHIVE_COMMAND, "openspec archive"));
    refusals.addAll(targets.refusals());
    return new Evaluation(targets.targets(), refusals);
  }

  private Evaluation targets(
      final Path root, final GuardRequest request, final List<GuardTarget> targets) {
    final GuardTargetResolver resolver =
        new GuardTargetResolver(paths, root, request.workingDirectory());
    return new Evaluation(
        targets.size(), targets.stream().map(resolver::refusal).flatMap(Optional::stream).toList());
  }

  private static Evaluation uninspectable(final String description) {
    return new Evaluation(
        0, List.of(GuardRefusal.of(GuardRefusal.Reason.UNINSPECTABLE, description)));
  }

  private static String describe(
      final GuardRequest request, final Evaluation evaluation, final long started) {
    final String reasons =
        evaluation.refusals().stream()
            .map(refusal -> refusal.reason().name().toLowerCase(Locale.ROOT))
            .distinct()
            .collect(Collectors.joining(","));
    return "event=spec-guard-decision kind="
        + kind(request)
        + " targets="
        + evaluation.targets()
        + " refusals="
        + evaluation.refusals().size()
        + " reasons="
        + (reasons.isEmpty() ? "none" : reasons)
        + " decision="
        + (evaluation.refusals().isEmpty() ? "allowed" : "refused")
        + " durationMs="
        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
  }

  private static String kind(final GuardRequest request) {
    return request.kind().name().toLowerCase(Locale.ROOT);
  }

  /** The number of inspected targets and the refusals they produced. */
  private record Evaluation(int targets, List<GuardRefusal> refusals) {}
}
