package dev.sentinel.application.doctor;

import com.google.inject.Inject;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DoctorService {
  public enum Status {
    OK,
    WARNING,
    ERROR
  }

  public record Finding(String name, Status status, String message) {}

  private final ProjectDetector detector;
  private final EnvironmentInspection environment;

  @Inject
  public DoctorService(ProjectDetector detector, EnvironmentInspection environment) {
    this.detector = detector;
    this.environment = environment;
  }

  public List<Finding> diagnose(Path start) {
    List<Finding> findings = new ArrayList<>();
    var project = detector.detect(start);
    if (project.isEmpty()) {
      findings.add(new Finding("project", Status.ERROR, "No supported project detected."));
      return findings;
    }
    Path root = project.get().root();
    EnvironmentFacts facts = environment.inspect(root);
    findings.add(new Finding("project", Status.OK, root.toString()));
    boolean mavenAvailable =
        Boolean.parseBoolean(facts.values().get("mavenWrapperAvailable"))
            || facts.hasExecutable("mvn");
    findings.add(
        new Finding(
            "maven",
            mavenAvailable ? Status.OK : Status.ERROR,
            project.get().mavenWrapperAvailable()
                ? "Maven Wrapper available."
                : "System Maven is required."));
    boolean configPresent = Boolean.parseBoolean(facts.values().get("configurationPresent"));
    findings.add(
        new Finding(
            "configuration",
            configPresent ? Status.OK : Status.ERROR,
            configPresent ? "Configuration is present." : "Run 'sentinel init'."));
    boolean integrationPresent = Boolean.parseBoolean(facts.values().get("openCodeCommandPresent"));
    findings.add(
        new Finding(
            "integration",
            integrationPresent ? Status.OK : Status.WARNING,
            "OpenCode integration " + (integrationPresent ? "present." : "not configured.")));
    boolean nativePresent = Boolean.parseBoolean(facts.values().get("nativeExecutablePresent"));
    findings.add(
        new Finding(
            "native",
            nativePresent ? Status.OK : Status.WARNING,
            nativePresent ? "Native executable present." : "Build with the native profile."));
    return findings;
  }
}
