package dev.sentinel.application.doctor;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

public final class DoctorService {
  private final ProjectDetector detector;
  private final EnvironmentInspection environment;

  public enum Status {
    OK,
    WARNING,
    ERROR
  }

  public record Finding(String name, Status status, String message) {}

  @Inject
  public DoctorService(final ProjectDetector detector, final EnvironmentInspection environment) {
    this.detector = detector;
    this.environment = environment;
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
    return findings;
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
