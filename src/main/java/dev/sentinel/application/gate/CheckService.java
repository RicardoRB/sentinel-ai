package dev.sentinel.application.gate;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.config.PartialCheckPlan;
import dev.sentinel.domain.config.PathExistence;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

public class CheckService {
  private static final Logger LOGGER = Logger.getLogger(CheckService.class.getName());

  private final ProjectDetector detector;
  private final SentinelConfigurationReader configurationReader;
  private final QualityGateFactory gateFactory;
  private final QualityGateRunner runner;
  private final PathExistence pathExistence;

  @Inject
  public CheckService(
      final ProjectDetector detector,
      final SentinelConfigurationReader configurationReader,
      final QualityGateFactory gateFactory,
      final QualityGateRunner runner,
      final PathExistence pathExistence) {
    this.detector = detector;
    this.configurationReader = configurationReader;
    this.gateFactory = gateFactory;
    this.runner = runner;
    this.pathExistence = pathExistence;
  }

  public CheckReport check(final Path start) {
    return check(start, List.of());
  }

  public CheckReport check(final Path start, final List<String> requestedProfiles) {
    return check(start, requestedProfiles, CheckProgressListener.NO_OP, false);
  }

  public CheckReport check(
      final Path start,
      final List<String> requestedProfiles,
      final CheckProgressListener listener) {
    return check(start, requestedProfiles, listener, false);
  }

  /** Runs the default profile gates except {@code excludedGates}, without progress output. */
  public CheckReport checkExcluding(final Path start, final Set<String> excludedGates) {
    return check(start, List.of(), CheckProgressListener.NO_OP, false, excludedGates);
  }

  /** Runs a partial check of the {@code default} profile, without progress output. */
  public CheckReport checkPartial(
      final Path start, final List<Path> paths, final Set<String> excludedGates) {
    return checkPartial(start, List.of(), paths, CheckProgressListener.NO_OP, false, excludedGates);
  }

  /**
   * Runs the selected gates for the edited {@code paths}. Each gate with a {@code partial} table is
   * skipped, narrowed to the paths, or run in full, as {@link PartialCheckPlan} decides. Without
   * paths every gate runs in full.
   */
  public CheckReport checkPartial(
      final Path start,
      final List<String> requestedProfiles,
      final List<Path> paths,
      final CheckProgressListener listener,
      final boolean failFast,
      final Set<String> excludedGates) {
    final Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    final Optional<SentinelConfiguration> selected =
        select(project, requestedProfiles, excludedGates);
    if (selected.isEmpty()) {
      return new CheckReport(project, List.of());
    }
    final PartialCheckPlan plan =
        PartialCheckPlan.create(project.root(), selected.get().gates(), paths, pathExistence);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=partial-plan paths="
                + paths.size()
                + " skipped="
                + plan.count(PartialCheckPlan.Scope.SKIP)
                + " narrowed="
                + plan.count(PartialCheckPlan.Scope.NARROW)
                + " full="
                + plan.count(PartialCheckPlan.Scope.FULL));
    final List<QualityGate> gates = PartialGates.create(selected.get(), plan, gateFactory, project);
    return PartialGates.scoped(runner.run(project, gates, listener, failFast), plan, paths);
  }

  /**
   * Runs only the configured gates whose IDs are in {@code gateIds}, after profile selection.
   * Unlike a full check, having none of them enabled is not an error: the report is empty.
   */
  public CheckReport check(
      final Path start, final List<String> requestedProfiles, final Set<String> gateIds) {
    final Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    final SentinelConfiguration selected =
        configurationReader
            .read(project.root().resolve(SentinelConfiguration.FILE_NAME))
            .forProfiles(requestedProfiles)
            .onlyGates(gateIds);
    LOGGER.log(
        Level.INFO, () -> "event=gate-subset-selected gates=" + selected.enabledGates().size());
    if (selected.enabledGates().isEmpty()) {
      return new CheckReport(project, List.of());
    }
    return runner.run(
        project, gateFactory.create(selected, project), CheckProgressListener.NO_OP, false);
  }

  public CheckReport check(
      final Path start,
      final List<String> requestedProfiles,
      final CheckProgressListener listener,
      final boolean failFast) {
    return check(start, requestedProfiles, listener, failFast, Set.of());
  }

  /**
   * Runs the selected gates except {@code excludedGates}. When the exclusion alone leaves nothing
   * enabled, the report is empty rather than an error, because the excluded gates run elsewhere.
   */
  public CheckReport check(
      final Path start,
      final List<String> requestedProfiles,
      final CheckProgressListener listener,
      final boolean failFast,
      final Set<String> excludedGates) {
    final Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    final Optional<SentinelConfiguration> selected =
        select(project, requestedProfiles, excludedGates);
    if (selected.isEmpty()) {
      return new CheckReport(project, List.of());
    }
    final List<QualityGate> gates = gateFactory.create(selected.get(), project);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=configuration-selected profiles="
                + selected.get().profileNames().size()
                + " gates="
                + gates.size());
    return runner.run(project, gates, listener, failFast);
  }

  /**
   * Selects the profiles' gates except {@code excludedGates}. When the exclusion alone leaves
   * nothing enabled, the result is empty rather than an error, because the excluded gates run
   * elsewhere.
   */
  private Optional<SentinelConfiguration> select(
      final Project project,
      final List<String> requestedProfiles,
      final Set<String> excludedGates) {
    final SentinelConfiguration profiled =
        configurationReader
            .read(project.root().resolve(SentinelConfiguration.FILE_NAME))
            .forProfiles(requestedProfiles);
    final SentinelConfiguration selected = profiled.withoutGates(excludedGates);
    if (!profiled.enabledGates().isEmpty() && selected.enabledGates().isEmpty()) {
      LOGGER.log(Level.INFO, "event=configuration-selected gates=0 reason=all-excluded");
      return Optional.empty();
    }
    if (selected.enabledGates().isEmpty()) {
      throw new SentinelException(
          "No quality gates are enabled in "
              + SentinelConfiguration.FILE_NAME
              + "; refusing to report a pass without checking anything.");
    }
    return Optional.of(selected);
  }
}
