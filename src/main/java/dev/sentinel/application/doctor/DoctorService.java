package dev.sentinel.application.doctor;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.ExecutableObservation;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

public final class DoctorService {
  private final ProjectDetector detector;
  private final EnvironmentInspection environment;
  private final SentinelConfigurationReader configurationReader;

  public enum Status {
    OK,
    WARNING,
    ERROR
  }

  public record Finding(String name, Status status, String message) {}

  @Inject
  public DoctorService(
      final ProjectDetector detector,
      final EnvironmentInspection environment,
      final SentinelConfigurationReader configurationReader) {
    this.detector = detector;
    this.environment = environment;
    this.configurationReader = configurationReader;
  }

  public DoctorService(final ProjectDetector detector, final EnvironmentInspection environment) {
    this(detector, environment, null);
  }

  public List<Finding> diagnose(final Path start) {
    final List<Finding> findings = new ArrayList<>();
    final Optional<Project> project = detector.detect(start);
    if (project.isEmpty()) {
      findings.add(new Finding("project", Status.ERROR, "No supported project detected."));
      return findings;
    }
    final Path root = project.get().root();
    final EnvironmentFacts facts = environment.inspect(root);
    final String mavenMessage =
        project.get().mavenWrapperAvailable()
            ? "Maven Wrapper available."
            : "System Maven is required.";
    final boolean openCode = flag(facts, "openCodeCommandPresent");
    findings.add(new Finding("project", Status.OK, root.toString()));
    findings.add(
        check(
            "maven",
            flag(facts, "mavenWrapperAvailable") || facts.hasExecutable("mvn"),
            Status.ERROR,
            mavenMessage,
            mavenMessage));
    findings.add(
        check(
            "configuration",
            flag(facts, "configurationPresent"),
            Status.ERROR,
            "Configuration is present.",
            "Run 'sentinel init'."));
    findings.add(
        new Finding(
            "integration",
            openCode ? Status.OK : Status.WARNING,
            "OpenCode integration " + (openCode ? "present." : "not configured.")));
    findings.add(
        check(
            "native",
            flag(facts, "nativeExecutablePresent"),
            Status.WARNING,
            "Native executable present.",
            "Build with the native profile."));
    diagnoseOpenSpec(root, facts, findings);
    OpenSpecGuidanceLayout.all().forEach(layout -> diagnoseGuidance(root, facts, layout, findings));
    return findings;
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

  private void diagnoseGuidance(
      final Path root,
      final EnvironmentFacts facts,
      final OpenSpecGuidanceLayout layout,
      final List<Finding> findings) {
    final String agent = layout.agentId();
    if (!flag(facts, "openSpecGuidance." + agent)) {
      return;
    }
    final List<String> remediations = new ArrayList<>();
    if (!facts.hasExecutable("openspec")) {
      remediations.add(
          "OpenSpec is not on PATH; a human must install OpenSpec so `openspec` is on PATH.");
    }
    if (!environment.hasOpenSpecDirectory(root)) {
      remediations.add("openspec/ is missing; a human must run `openspec init`.");
    }
    if (!flag(facts, "openSpecVerifyWorkflow." + agent)) {
      remediations.add(
          "/opsx:verify is not installed; a human must run `openspec config profile`, include"
              + " verify, then `openspec update`.");
    }
    findings.add(
        remediations.isEmpty()
            ? new Finding(
                "guidance", Status.OK, agent + " OpenSpec guidance installed; prerequisites ready.")
            : new Finding(
                "guidance",
                Status.WARNING,
                agent + " OpenSpec guidance installed. " + String.join(" ", remediations)));
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
