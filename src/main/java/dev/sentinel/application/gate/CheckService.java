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
import java.util.Set;
import javax.inject.Inject;

public class CheckService {

  private final ProjectDetector detector;
  private final SentinelConfigurationReader configurationReader;
  private final QualityGateFactory gateFactory;
  private final QualityGateRunner runner;

  @Inject
  public CheckService(
      ProjectDetector detector,
      SentinelConfigurationReader configurationReader,
      QualityGateFactory gateFactory,
      QualityGateRunner runner) {
    this.detector = detector;
    this.configurationReader = configurationReader;
    this.gateFactory = gateFactory;
    this.runner = runner;
  }

  public CheckReport check(Path start) {
    return check(start, List.of());
  }

  public CheckReport check(Path start, List<String> requestedProfiles) {
    Project project = detector.detect(start).orElseThrow(ProjectNotFoundException::new);
    SentinelConfiguration configuration =
        configurationReader.read(project.root().resolve(SentinelConfiguration.FILE_NAME));
    SentinelConfiguration selected = selectProfiles(configuration, requestedProfiles);
    List<QualityGate> gates = gateFactory.create(selected, project);
    if (selected.enabledGates().isEmpty()) {
      throw new SentinelException(
          "No quality gates are enabled in "
              + SentinelConfiguration.FILE_NAME
              + "; refusing to report a pass without checking anything.");
    }
    return runner.run(project, gates);
  }

  private static SentinelConfiguration selectProfiles(
      SentinelConfiguration configuration, List<String> requested) {
    List<String> names =
        requested == null || requested.isEmpty()
            ? List.of(SentinelConfiguration.DEFAULT_PROFILE)
            : requested;
    if (names.stream().anyMatch(name -> name == null || name.isBlank())) {
      throw new SentinelException("Profile names must not be blank.");
    }
    Set<String> selectedNames = new LinkedHashSet<>(names);
    if (selectedNames.contains(SentinelConfiguration.DEFAULT_PROFILE)
        && !configuration.profileNames().contains(SentinelConfiguration.DEFAULT_PROFILE)) {
      throw new SentinelException(
          "Nothing belongs to the default profile; pass --profile or add default to a gate's profiles.");
    }
    Set<String> unknown = new LinkedHashSet<>(selectedNames);
    unknown.removeAll(configuration.profileNames());
    if (!unknown.isEmpty()) {
      throw new SentinelException(
          "Unknown profiles " + unknown + ". Available profiles: " + configuration.profileNames());
    }
    var selected = new LinkedHashMap<String, GateConfiguration>();
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
}
