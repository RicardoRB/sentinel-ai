package dev.ward.application.spec;

import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.project.Project;
import dev.ward.domain.spec.GuardDecision;
import dev.ward.domain.spec.GuardFeedback;
import dev.ward.domain.spec.GuardOperation;
import dev.ward.domain.spec.GuardRefusal;
import dev.ward.domain.spec.GuardRequest;
import dev.ward.domain.spec.GuardTarget;
import dev.ward.domain.spec.PatchTargets;
import dev.ward.domain.spec.ProjectPaths;
import dev.ward.domain.spec.ShellCommandHeuristic;
import dev.ward.domain.spec.ShellInspection;
import dev.ward.domain.spec.SkillPolicy;
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
    final Optional<Project> project = detector.detect(request.start());
    final Optional<Path> root = project.map(this::openSpecRoot).orElseGet(() -> outside(request));
    if (root.isEmpty()) {
      LOGGER.log(
          Level.INFO,
          () -> "event=spec-guard-skipped kind=" + kind(request) + " reason=no-openspec");
      return GuardDecision.ALLOWED;
    }
    final Evaluation evaluation =
        evaluate(
            root.get(),
            project.map(found -> fromProject(found, root.get(), request)).orElse(request));
    LOGGER.log(Level.INFO, () -> describe(request, evaluation, started));
    return evaluation.refusals().isEmpty()
        ? GuardDecision.ALLOWED
        : GuardDecision.refused(
            GuardFeedback.render(evaluation.refusals(), paths.activeChanges(root.get())));
  }

  /**
   * The directory whose {@code openspec/} is protected: the project's own, or for a nested project
   * without one, the repository root's, as init configures it. The guard reads no configuration.
   */
  private Optional<Path> openSpecRoot(final Project project) {
    if (paths.isDirectory(project.root(), OPENSPEC)) {
      return Optional.of(project.root());
    }
    return project.nested() && paths.isDirectory(project.repositoryRoot(), OPENSPEC)
        ? Optional.of(project.repositoryRoot())
        : Optional.empty();
  }

  /**
   * A repository-level hook starts outside every project; it protects the {@code openspec/} of the
   * directory it starts in, where a monorepo keeps the specs its projects share.
   *
   * @throws ProjectNotFoundException when that directory has no {@code openspec/} either
   */
  private Optional<Path> outside(final GuardRequest request) {
    final Path start = request.start().toAbsolutePath().normalize();
    if (!paths.isDirectory(start, OPENSPEC)) {
      throw new ProjectNotFoundException();
    }
    return Optional.of(start);
  }

  /** Relative paths start from the project, also when the protected root sits above it. */
  private static GuardRequest fromProject(
      final Project project, final Path root, final GuardRequest request) {
    if (root.equals(project.root())) {
      return request;
    }
    final String start =
        project.root().toAbsolutePath().resolve(request.workingDirectory()).toString();
    return new GuardRequest(request.start(), start, request.kind(), request.values());
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
