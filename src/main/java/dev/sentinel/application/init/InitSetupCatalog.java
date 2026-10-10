package dev.sentinel.application.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.init.InitArchitectureOption;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Stable choices and project-aware defaults used by the interactive init flow. */
public final class InitSetupCatalog {
  public static final String NO_INTEGRATION = "none";
  private final EnvironmentInspection environment;

  @Inject
  public InitSetupCatalog(final EnvironmentInspection environment) {
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

  public InitArchitectureOption architecture(final String id) {
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

  public List<InitGateOption> gates(final Project project) {
    final String executable = project.mavenWrapperAvailable() ? "./mvnw" : "mvn";
    final boolean mavenAvailable =
        project.mavenWrapperAvailable() || environment.inspect(project.root()).hasExecutable("mvn");
    final String availability =
        mavenAvailable
            ? "Maven command available: " + executable
            : "Maven is unavailable; install Maven or add a Maven Wrapper before running this gate.";
    final List<InitGateOption> options = new ArrayList<>();
    options.add(
        option(
            "tests",
            "Runs the project's tests.",
            mavenAvailable,
            availability,
            executable,
            "test"));
    options.add(
        option(
            "compile",
            "Checks that the project compiles.",
            mavenAvailable,
            availability,
            executable,
            "compile"));
    options.add(
        option(
            "coverage",
            "Enforces minimum test coverage.",
            mavenAvailable,
            availability + " (minimum coverage is configured in pom.xml.)",
            executable,
            "jacoco:check"));
    options.add(
        option(
            "spotbugs",
            "Finds potential bugs through static analysis.",
            mavenAvailable,
            availability,
            executable,
            "spotbugs:check"));
    options.add(
        option(
            "checkstyle",
            "Checks source style and formatting rules.",
            mavenAvailable,
            availability,
            executable,
            "checkstyle:check"));
    options.add(
        option(
            "pmd",
            "Finds common Java design and implementation problems with PMD.",
            mavenAvailable,
            availability,
            executable,
            "pmd:check"));
    options.add(
        option(
            "sonar",
            "Checks code quality, smells, and duplication.",
            mavenAvailable,
            availability,
            executable,
            "sonar"));
    options.add(
        option(
            "dependency-check",
            "Scans dependencies for known CVEs.",
            mavenAvailable,
            availability,
            executable,
            "dependency-check:check"));
    options.add(
        option(
            "archunit",
            "Checks architecture rules with ArchitectureTest.",
            mavenAvailable,
            availability + " (runs only ArchitectureTest.)",
            executable,
            "-Dtest=ArchitectureTest",
            "test"));
    options.add(
        option(
            "mutation",
            "Measures test strength with mutation testing.",
            mavenAvailable,
            availability,
            executable,
            "pitest:mutationCoverage"));
    options.add(
        option(
            "format",
            "Checks formatting with Spotless and google-java-format.",
            mavenAvailable,
            availability,
            executable,
            "spotless:check"));
    options.add(
        binaryOption(
            project,
            "semgrep",
            "Runs SAST and custom security rules with Semgrep.",
            List.of("semgrep", "scan", "--config", "p/java", "--error"),
            "Install Semgrep (needs network access to fetch the p/java ruleset)."));
    options.add(
        binaryOption(
            project,
            "gitleaks",
            "Detects committed secrets with Gitleaks.",
            List.of("gitleaks", "detect", "--no-banner", "--redact"),
            "Install Gitleaks."));
    options.add(
        binaryOption(
            project,
            "zap",
            "Runs an OWASP ZAP baseline DAST scan against a running application.",
            List.of("zap-baseline.py", "-t", SupportedQualityGates.ZAP_TARGET_PLACEHOLDER),
            "Install ZAP and replace "
                + SupportedQualityGates.ZAP_TARGET_PLACEHOLDER
                + " with a target you are authorized to scan before running."));
    options.add(
        binaryOption(
            project,
            "trivy",
            "Scans dependencies, containers, and IaC with Trivy.",
            List.of("trivy", "fs", "--exit-code", "1", "."),
            "Install Trivy."));
    options.add(
        option(
            "enforcer",
            "Enforces dependency and build rules with Maven Enforcer.",
            mavenAvailable,
            availability + " (dependency and build rules are configured in pom.xml.)",
            executable,
            "enforcer:enforce"));
    options.add(
        option(
            "license",
            "Checks dependency license compliance with the License Maven Plugin.",
            mavenAvailable,
            availability + " (license policy is configured in pom.xml.)",
            executable,
            "license:add-third-party"));
    options.add(
        option(
            "api-compat",
            "Checks binary/API compatibility between versions with japicmp.",
            mavenAvailable,
            availability + " (the comparison baseline is configured in pom.xml.)",
            executable,
            "japicmp:cmp"));
    options.add(
        option(
            "command",
            "Runs a custom command from sentinel.toml.",
            mavenAvailable,
            availability + " (customize the command in sentinel.toml after initialization.)",
            executable,
            "test"));
    return List.copyOf(options);
  }

  public InitIntegrationOption integration(final String id) {
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

  public InitGateOption gate(final Project project, final String id) {
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

  public List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  public QualityPreset preset(final String id) {
    return presets().stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown preset '" + id + "'. Supported presets: standard, strict"));
  }

  private InitGateOption binaryOption(
      final Project project,
      final String id,
      final String description,
      final List<String> command,
      final String hint) {
    final boolean available = environment.inspect(project.root()).hasExecutable(command.getFirst());
    final String message =
        available
            ? "Executable available: " + command.getFirst()
            : command.getFirst() + " is not on the path. " + hint;
    return new InitGateOption(id, description, command, available, message);
  }

  private static InitGateOption option(
      final String id,
      final String description,
      final boolean available,
      final String message,
      final String... command) {
    return new InitGateOption(id, description, List.of(command), available, message);
  }
}
