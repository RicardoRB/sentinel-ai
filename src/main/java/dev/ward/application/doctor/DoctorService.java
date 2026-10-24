package dev.ward.application.doctor;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.project.Project;
import dev.ward.domain.traceability.TraceabilityPrerequisites;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

/** Diagnoses whether a project and its environment are ready for Ward. */
public final class DoctorService {
  private final ProjectDetector detector;
  private final EnvironmentInspection environment;
  private final WardConfigurationReader configurationReader;
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
      final WardConfigurationReader configurationReader,
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

  /** Creates a service that also checks {@code ward.toml}. */
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final WardConfigurationReader configurationReader) {
    this(detector, environment, configurationReader, null, Set.of());
  }

  /** Creates a service that also checks the traceability prerequisites. */
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final WardConfigurationReader configurationReader,
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
    final boolean openCode = flag(facts, "openCodeCommandPresent");
    findings.add(new Finding("project", Status.OK, root.toString()));
    findings.addAll(BuildToolFindings.of(detected, facts));
    findings.add(
        check(
            "configuration",
            flag(facts, "configurationPresent"),
            Status.ERROR,
            "Configuration is present.",
            "Run 'ward init'."));
    diagnoseIntegrations(project.get(), openCode, findings);
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
      final WardConfiguration configuration =
          configurationReader.read(root.resolve(WardConfiguration.FILE_NAME));
      final GateConfiguration gate = configuration.gates().get("dependency-check");
      if (gate != null && gate.enabled()) {
        findings.addAll(
            DependencyCheckDiagnosis.findings(facts, environment.ossIndexSetting(root)));
      }
    } catch (WardException ignored) {
      // The configuration finding already reports malformed configuration.
    }
  }

  private void diagnoseIntegrations(
      final Project project, final boolean legacyOpenCode, final List<Finding> findings) {
    final List<AgentIntegration> configured =
        integrations.stream()
            .filter(
                adapter -> health(project, adapter).status() != IntegrationHealth.Status.MISSING)
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
                        + health(project, adapter).status().name().toLowerCase(Locale.ROOT)
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
                adapter -> health(project, adapter).status() == IntegrationHealth.Status.INSTALLED);
    findings.add(
        new Finding(
            "integration",
            healthy ? Status.OK : Status.WARNING,
            "Configured integrations: "
                + summary
                + (healthy ? "." : "; re-run integrate to repair.")));
  }

  /** Nested projects find integrations that install at the repository root there. */
  private static IntegrationHealth health(final Project project, final AgentIntegration adapter) {
    final boolean shared = adapter.installsAtRepositoryRoot();
    return adapter.health(project.installRoot(shared), project.installPath(shared));
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
    final WardConfiguration configuration;
    try {
      configuration = configurationReader.read(root.resolve(WardConfiguration.FILE_NAME));
    } catch (WardException e) {
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
    final WardConfiguration configuration;
    try {
      configuration = configurationReader.read(root.resolve(WardConfiguration.FILE_NAME));
    } catch (WardException e) {
      findings.add(
          new Finding(
              "configuration",
              Status.ERROR,
              "Configuration could not be read. Fix ward.toml and run doctor again."));
      return;
    }
    configuration.gates().entrySet().stream()
        .filter(entry -> isOpenSpec(entry.getKey()) && entry.getValue().enabled())
        .forEach(entry -> findings.addAll(OpenSpecDoctorFindings.of(environment, root, entry)));
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
