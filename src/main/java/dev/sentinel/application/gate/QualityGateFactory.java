package dev.sentinel.application.gate;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.gate.ReportDirectories;
import dev.sentinel.domain.gate.SkippedQualityGate;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.traceability.ScenarioInventory;
import dev.sentinel.domain.traceability.TestMappingInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Resolves the typed built-in gate registry in stable configuration order. */
public class QualityGateFactory {
  private static final String ZAP = "zap";

  public static final List<String> SUPPORTED_GATES = SupportedQualityGates.IDS;

  private final CommandExecutor executor;
  private final EnvironmentInspection environment;
  private final ScenarioInventory scenarioInventory;
  private final TestMappingInventory testMappingInventory;
  private final ReportDirectories reportDirectories;

  /** Creates a factory for command gates only. */
  public QualityGateFactory(final CommandExecutor executor) {
    this(executor, null, null, null);
  }

  /** Creates a factory that can check executable availability. */
  public QualityGateFactory(
      final CommandExecutor executor, final EnvironmentInspection environment) {
    this(executor, environment, null, null);
  }

  /** Creates a factory that can also build the traceability gate. */
  public QualityGateFactory(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final ScenarioInventory scenarioInventory,
      final TestMappingInventory testMappingInventory) {
    this(executor, environment, scenarioInventory, testMappingInventory, ReportDirectories.NONE);
  }

  /**
   * Creates the factory. {@code reportDirectories} tells command gates where their tools write
   * reports.
   */
  @Inject
  public QualityGateFactory(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final ScenarioInventory scenarioInventory,
      final TestMappingInventory testMappingInventory,
      final ReportDirectories reportDirectories) {
    this.reportDirectories = reportDirectories;
    this.executor = executor;
    this.environment = environment;
    this.scenarioInventory = scenarioInventory;
    this.testMappingInventory = testMappingInventory;
  }

  /** Creates one gate per configured ID, without project-specific commands. */
  public List<QualityGate> create(final SentinelConfiguration configuration) {
    return create(configuration, null);
  }

  /**
   * Creates one gate per configured ID; disabled gates become skipped gates.
   *
   * @throws SentinelException if a gate ID is unknown
   */
  public List<QualityGate> create(
      final SentinelConfiguration configuration, final Project project) {
    final List<QualityGate> gates = new ArrayList<>();
    configuration
        .gates()
        .keySet()
        .forEach(
            id -> {
              if (!SUPPORTED_GATES.contains(id) && !SupportedQualityGates.LEGACY_IDS.contains(id)) {
                throw new SentinelException(
                    "Quality gate '" + id + "' is unknown. Supported gates: " + SUPPORTED_GATES);
              }
            });
    for (final Map.Entry<String, GateConfiguration> entry : configuration.gates().entrySet()) {
      final String id = entry.getKey();
      if (!entry.getValue().enabled()) {
        gates.add(new SkippedQualityGate(id));
        continue;
      }
      gates.add(createGate(id, entry.getValue(), project));
    }
    return gates;
  }

  private QualityGate createGate(
      final String id, final GateConfiguration configuration, final Project project) {
    if (OpenSpecQualityGate.ACTIVE.equals(id) || OpenSpecQualityGate.ARCHIVED.equals(id)) {
      return environment == null
          ? new CommandQualityGate(id, executor, configuration.command())
          : new OpenSpecQualityGate(id, executor, environment, configuration.command());
    }
    if (MavenTestGate.NAME.equals(id)) {
      return new MavenTestGate(executor, testsCommand(configuration.command(), project));
    }
    if (OpenSpecJunitTraceabilityGate.NAME.equals(id)) {
      return traceabilityGate();
    }
    if (ZAP.equals(id)) {
      ZapTargetValidation.require(id, configuration.command());
    }
    return new CommandQualityGate(id, executor, configuration.command(), reportDirectories);
  }

  private QualityGate traceabilityGate() {
    if (scenarioInventory == null || testMappingInventory == null) {
      throw new SentinelException("Traceability gate dependencies are unavailable.");
    }
    return new OpenSpecJunitTraceabilityGate(scenarioInventory, testMappingInventory);
  }

  private static List<String> testsCommand(final List<String> configured, final Project project) {
    if (project != null
        && !project.wrapperAvailable()
        && !configured.isEmpty()
        && ("./mvnw".equals(configured.getFirst())
            || "mvnw".equals(configured.getFirst())
            || "./gradlew".equals(configured.getFirst())
            || "gradlew".equals(configured.getFirst()))) {
      final List<String> fallback = new ArrayList<>(configured);
      fallback.set(0, project.buildTool() == BuildTool.GRADLE ? "gradle" : "mvn");
      return fallback;
    }
    return configured;
  }
}
