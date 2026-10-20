package dev.sentinel.application.doctor;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationHealth;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.ExecutableObservation;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.traceability.TraceabilityPrerequisites;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** Diagnoses whether a project and its environment are ready for Sentinel. */
public final class DoctorService {
  private final ProjectDetector detector;
  private final EnvironmentInspection environment;
  private final SentinelConfigurationReader configurationReader;
  private final TraceabilityPrerequisites traceabilityPrerequisites;
  private final Set<AgentIntegration> integrations;

  /** How serious a diagnostic finding is. */
  public enum Status {
    OK,
    WARNING,
    ERROR
  }

  /**
   * One diagnostic result.
   *
   * @param name what was checked
   * @param status how serious the result is
   * @param message what was found and how to fix it
   */
  public record Finding(String name, Status status, String message) {}

  /**
   * Creates the service. {@code traceabilityPrerequisites} checks the traceability gate's inputs,
   * and {@code integrations} are checked for installation health.
   */
  @Inject
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final SentinelConfigurationReader configurationReader,
      final TraceabilityPrerequisites traceabilityPrerequisites,
      final Set<AgentIntegration> integrations) {
    this.detector = detector;
    this.environment = environment;
    this.configurationReader = configurationReader;
    this.traceabilityPrerequisites = traceabilityPrerequisites;
    this.integrations = Set.copyOf(integrations);
  }

  /** Creates a service that checks only the project and environment. */
  public DoctorService(final ProjectDetector detector, final EnvironmentInspection environment) {
    this(detector, environment, null, null, Set.of());
  }

  /** Creates a service that also checks {@code sentinel.toml}. */
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final SentinelConfigurationReader configurationReader) {
    this(detector, environment, configurationReader, null, Set.of());
  }

  /** Creates a service that also checks the traceability prerequisites. */
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final SentinelConfigurationReader configurationReader,
      final TraceabilityPrerequisites traceabilityPrerequisites) {
    this(detector, environment, configurationReader, traceabilityPrerequisites, Set.of());
  }

  /** Returns the diagnostic findings for the project containing {@code start}. */
  public List<Finding> diagnose(final Path start) {
    final List<Finding> findings = new ArrayList<>();
    final Optional<Project> project = detector.detect(start);
    if (project.isEmpty()) {
      findings.add(new Finding("project", Status.ERROR, "No supported project detected."));
      return findings;
    }
    final Path root = project.get().root();
    final EnvironmentFacts facts = environment.inspect(root);
    final Project detected = project.get();
    final String toolName = detected.buildTool().displayName();
    final String wrapperName =
        detected.buildTool() == BuildTool.GRADLE ? "Gradle Wrapper" : "Maven Wrapper";
    final String executable = detected.buildTool() == BuildTool.GRADLE ? "gradle" : "mvn";
    final String wrapperFlag =
        detected.buildTool() == BuildTool.GRADLE
            ? "gradleWrapperAvailable"
            : "mavenWrapperAvailable";
    final String toolMessage =
        flag(facts, wrapperFlag)
            ? wrapperName + " available."
            : "System " + toolName + " is required.";
    final boolean openCode = flag(facts, "openCodeCommandPresent");
    findings.add(new Finding("project", Status.OK, root.toString()));
    findings.add(
        check(
            toolName.toLowerCase(Locale.ROOT),
            flag(facts, wrapperFlag) || facts.hasExecutable(executable),
            Status.ERROR,
            toolMessage,
            toolMessage));
    findings.add(
        check(
            "configuration",
            flag(facts, "configurationPresent"),
            Status.ERROR,
            "Configuration is present.",
            "Run 'sentinel init'."));
    diagnoseIntegrations(root, openCode, findings);
    findings.add(
        check(
            "native",
            flag(facts, "nativeExecutablePresent"),
            Status.WARNING,
            "Native executable present.",
            "Build with the native profile."));
    diagnoseOpenSpec(root, facts, findings);
    diagnoseNvdApiKey(root, facts, findings);
    diagnoseTraceability(root, findings);
    return findings;
  }

  private void diagnoseNvdApiKey(
      final Path root, final EnvironmentFacts facts, final List<Finding> findings) {
    if (configurationReader == null || !flag(facts, "configurationPresent")) {
      return;
    }
    try {
      final SentinelConfiguration configuration =
          configurationReader.read(root.resolve(SentinelConfiguration.FILE_NAME));
      final GateConfiguration gate = configuration.gates().get("dependency-check");
      if (gate != null && gate.enabled()) {
        findings.addAll(
            DependencyCheckDiagnosis.findings(facts, environment.ossIndexSetting(root)));
      }
    } catch (SentinelException ignored) {
      // The configuration finding already reports malformed configuration.
    }
  }

  private void diagnoseIntegrations(
      final Path root, final boolean legacyOpenCode, final List<Finding> findings) {
    final List<AgentIntegration> configured =
        integrations.stream()
            .filter(adapter -> adapter.health(root).status() != IntegrationHealth.Status.MISSING)
            .toList();
    if (configured.isEmpty()) {
      findings.add(
          new Finding(
              "integration",
              legacyOpenCode ? Status.OK : Status.WARNING,
              legacyOpenCode
                  ? "OpenCode integration present."
                  : "No supported agent integration configured."));
      return;
    }
    final String summary =
        configured.stream()
            .map(
                adapter ->
                    adapter.id()
                        + "="
                        + adapter.health(root).status().name().toLowerCase(Locale.ROOT)
                        + " ["
                        + adapter.capabilities().stream()
                            .map(IntegrationCapability::name)
                            .sorted()
                            .toList()
                        + "]"
                        + (adapter.capabilities().contains(IntegrationCapability.PRE_TOOL)
                            ? ""
                            : " (advisory-only; no pre-tool enforcement)"))
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    final boolean healthy =
        configured.stream()
            .allMatch(
                adapter -> adapter.health(root).status() == IntegrationHealth.Status.INSTALLED);
    findings.add(
        new Finding(
            "integration",
            healthy ? Status.OK : Status.WARNING,
            "Configured integrations: "
                + summary
                + (healthy ? "." : "; re-run integrate to repair.")));
  }

  private void diagnoseTraceability(final Path root, final List<Finding> findings) {
    if (configurationReader == null || traceabilityPrerequisites == null) {
      return;
    }
    if (!traceabilityEnabled(root)) {
      return;
    }
    final boolean specs = traceabilityPrerequisites.liveSpecsDirectory(root);
    final boolean tests = traceabilityPrerequisites.javaTestSourceDirectory(root);
    findings.add(traceabilityFinding(specs, tests));
  }

  private boolean traceabilityEnabled(final Path root) {
    final SentinelConfiguration configuration;
    try {
      configuration = configurationReader.read(root.resolve(SentinelConfiguration.FILE_NAME));
    } catch (SentinelException e) {
      return false;
    }
    final GateConfiguration gate = configuration.gates().get("openspec-junit-traceability");
    return gate != null && gate.enabled();
  }

  private static Finding traceabilityFinding(final boolean specs, final boolean tests) {
    final boolean ready = specs && tests;
    return new Finding(
        "openspec-junit-traceability",
        ready ? Status.OK : Status.ERROR,
        ready
            ? "Live OpenSpec specs and Java test sources are available."
            : "Enabled traceability requires openspec/specs/ and src/test/java/.");
  }

  private void diagnoseOpenSpec(
      final Path root, final EnvironmentFacts facts, final List<Finding> findings) {
    if (configurationReader == null || !flag(facts, "configurationPresent")) {
      return;
    }
    final SentinelConfiguration configuration;
    try {
      configuration = configurationReader.read(root.resolve(SentinelConfiguration.FILE_NAME));
    } catch (SentinelException e) {
      findings.add(
          new Finding(
              "configuration",
              Status.ERROR,
              "Configuration could not be read. Fix sentinel.toml and run doctor again."));
      return;
    }
    configuration.gates().entrySet().stream()
        .filter(entry -> isOpenSpec(entry.getKey()) && entry.getValue().enabled())
        .forEach(entry -> addOpenSpecFinding(root, entry, findings));
  }

  private void addOpenSpecFinding(
      final Path root,
      final Map.Entry<String, GateConfiguration> entry,
      final List<Finding> findings) {
    final String executable =
        entry.getValue().command().isEmpty() ? "" : entry.getValue().command().getFirst();
    final ExecutableObservation observation = environment.inspectExecutable(root, executable);
    final boolean directory = environment.hasOpenSpecDirectory(root);
    if (observation.usable() && directory) {
      findings.add(
          new Finding(
              entry.getKey(),
              Status.OK,
              "OpenSpec executable and project directory are available."));
      return;
    }
    final String executableMessage =
        observation.usable() ? "executable available" : "executable unavailable";
    final String directoryMessage =
        directory
            ? "project-root openspec/ directory available"
            : "project-root openspec/ directory missing";
    findings.add(
        new Finding(
            entry.getKey(),
            Status.ERROR,
            executableMessage
                + "; "
                + directoryMessage
                + ". Configure prerequisites before checking."));
  }

  private static boolean isOpenSpec(final String id) {
    return "openspec".equals(id) || "openspec-archived".equals(id);
  }

  private static boolean flag(final EnvironmentFacts facts, final String name) {
    return Boolean.parseBoolean(facts.values().get(name));
  }

  private static Finding check(
      final String name,
      final boolean passed,
      final Status failure,
      final String passedMessage,
      final String failedMessage) {
    return passed
        ? new Finding(name, Status.OK, passedMessage)
        : new Finding(name, failure, failedMessage);
  }
}
