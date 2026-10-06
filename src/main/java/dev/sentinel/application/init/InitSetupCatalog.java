package dev.sentinel.application.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.init.InitArchitectureOption;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Stable choices and project-aware defaults used by the interactive init flow. */
public final class InitSetupCatalog {
  public static final String NO_INTEGRATION = "none";
  private final EnvironmentInspection environment;

  @Inject
  public InitSetupCatalog(EnvironmentInspection environment) {
    this.environment = environment;
  }

  public List<InitIntegrationOption> integrations() {
    return List.of(
        new InitIntegrationOption(NO_INTEGRATION, "No agent integration"),
        new InitIntegrationOption("opencode", "OpenCode"),
        new InitIntegrationOption("claude-code", "Claude Code"));
  }

  public List<InitArchitectureOption> architectures() {
    return List.of(
        new InitArchitectureOption("layered", "Layered (controller -> service -> repository)"),
        new InitArchitectureOption("hexagonal", "Hexagonal (domain isolated from adapters)"),
        new InitArchitectureOption("clean", "Clean (domain isolated from outer layers)"));
  }

  public InitArchitectureOption architecture(String id) {
    return architectures().stream()
        .filter(option -> option.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown architecture '"
                        + id
                        + "'. Supported architectures: layered, hexagonal, clean"));
  }

  public List<InitGateOption> gates(Project project) {
    String executable = project.mavenWrapperAvailable() ? "./mvnw" : "mvn";
    boolean mavenAvailable =
        project.mavenWrapperAvailable() || environment.inspect(project.root()).hasExecutable("mvn");
    String availability =
        mavenAvailable
            ? "Maven command available: " + executable
            : "Maven is unavailable; install Maven or add a Maven Wrapper before running this gate.";
    List<InitGateOption> options = new ArrayList<>();
    options.add(
        option(
            "tests",
            "Runs the project's tests.",
            executable,
            "test",
            mavenAvailable,
            availability));
    options.add(
        option(
            "compile",
            "Checks that the project compiles.",
            executable,
            "compile",
            mavenAvailable,
            availability));
    options.add(
        option(
            "coverage",
            "Enforces minimum test coverage.",
            executable,
            "jacoco:check",
            mavenAvailable,
            availability + " (minimum coverage is configured in pom.xml.)"));
    options.add(
        option(
            "spotbugs",
            "Finds potential bugs through static analysis.",
            executable,
            "spotbugs:check",
            mavenAvailable,
            availability));
    options.add(
        option(
            "checkstyle",
            "Checks source style and formatting rules.",
            executable,
            "checkstyle:check",
            mavenAvailable,
            availability));
    options.add(
        option(
            "sonar",
            "Checks code quality, smells, and duplication.",
            executable,
            "sonar",
            mavenAvailable,
            availability));
    options.add(
        option(
            "dependency-check",
            "Scans dependencies for known CVEs.",
            executable,
            "dependency-check:check",
            mavenAvailable,
            availability));
    options.add(
        option(
            "archunit",
            "Checks architecture rules with ArchitectureTest.",
            executable,
            "-Dtest=ArchitectureTest",
            "test",
            mavenAvailable,
            availability + " (runs only ArchitectureTest.)"));
    options.add(
        option(
            "mutation",
            "Measures test strength with mutation testing.",
            executable,
            "pitest:mutationCoverage",
            mavenAvailable,
            availability));
    options.add(
        option(
            "compliance",
            "Checks dependency and build compliance rules.",
            executable,
            "enforcer:enforce",
            mavenAvailable,
            availability + " (dependency and build compliance rules are configured in pom.xml.)"));
    options.add(
        option(
            "command",
            "Runs a custom command from sentinel.toml.",
            executable,
            "test",
            mavenAvailable,
            availability + " (customize the command in sentinel.toml after initialization.)"));
    return List.copyOf(options);
  }

  public InitIntegrationOption integration(String id) {
    return integrations().stream()
        .filter(option -> option.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown integration '"
                        + id
                        + "'. Supported integrations: none, opencode, claude-code"));
  }

  public InitGateOption gate(Project project, String id) {
    return gates(project).stream()
        .filter(option -> option.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown quality gate '"
                        + id
                        + "'. Supported gates: "
                        + SupportedQualityGates.IDS));
  }

  private static InitGateOption option(
      String id,
      String description,
      String executable,
      String goal,
      boolean available,
      String message) {
    return new InitGateOption(id, description, List.of(executable, goal), available, message);
  }

  private static InitGateOption option(
      String id,
      String description,
      String executable,
      String firstGoal,
      String secondGoal,
      boolean available,
      String message) {
    return new InitGateOption(
        id, description, List.of(executable, firstGoal, secondGoal), available, message);
  }
}
