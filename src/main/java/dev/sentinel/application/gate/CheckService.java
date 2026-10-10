package dev.sentinel.application.gate;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

  @Inject
  public CheckService(
      final ProjectDetector detector,
      final SentinelConfigurationReader configurationReader,
      final QualityGateFactory gateFactory,
      final QualityGateRunner runner) {
    this.detector = detector;
    this.configurationReader = configurationReader;
    this.gateFactory = gateFactory;
    this.runner = runner;
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

  public CheckReport check(final Path start, final boolean failFast) {
    return check(start, List.of(), CheckProgressListener.NO_OP, failFast);
  }

  public CheckReport check(
      final Path start, final List<String> requestedProfiles, final boolean failFast) {
    return check(start, requestedProfiles, CheckProgressListener.NO_OP, failFast);
  }

  public CheckReport check(
      final Path start,
      final List<String> requestedProfiles,
      final CheckProgressListener listener,
      final boolean failFast) {
    final Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    final SentinelConfiguration configuration =
        configurationReader.read(project.root().resolve(SentinelConfiguration.FILE_NAME));
    final SentinelConfiguration selected = selectProfiles(configuration, requestedProfiles);
    final List<QualityGate> gates = gateFactory.create(selected, project);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=configuration-selected profiles="
                + selected.profileNames().size()
                + " gates="
                + gates.size());
    if (selected.enabledGates().isEmpty()) {
      throw new SentinelException(
          "No quality gates are enabled in "
              + SentinelConfiguration.FILE_NAME
              + "; refusing to report a pass without checking anything.");
    }
    return runner.run(project, gates, listener, failFast);
  }

  private static SentinelConfiguration selectProfiles(
      final SentinelConfiguration configuration, final List<String> requested) {
    final List<String> names =
        requested == null || requested.isEmpty()
            ? List.of(SentinelConfiguration.DEFAULT_PROFILE)
            : requested;
    if (names.stream().anyMatch(name -> name == null || name.isBlank())) {
      throw new SentinelException("Profile names must not be blank.");
    }
    final Set<String> selectedNames = new LinkedHashSet<>(names);
    requireKnownProfiles(configuration, selectedNames);
    final Map<String, GateConfiguration> selected = new LinkedHashMap<>();
    configuration
        .gates()
        .forEach(
            (gate, config) -> {
              if (config.profiles().stream().anyMatch(selectedNames::contains)) {
                selected.put(gate, config);
              }
            });
    return new SentinelConfiguration(configuration.version(), selected);
  }

  private static void requireKnownProfiles(
      final SentinelConfiguration configuration, final Set<String> selectedNames) {
    if (selectedNames.contains(SentinelConfiguration.DEFAULT_PROFILE)
        && !configuration.profileNames().contains(SentinelConfiguration.DEFAULT_PROFILE)) {
      throw new SentinelException(
          "Nothing belongs to the default profile; pass --profile or add default to a gate's profiles.");
    }
    final Set<String> unknown = new LinkedHashSet<>(selectedNames);
    unknown.removeAll(configuration.profileNames());
    if (!unknown.isEmpty()) {
      throw new SentinelException(
          "Unknown profiles " + unknown + ". Available profiles: " + configuration.profileNames());
    }
  }
}
