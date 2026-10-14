package dev.sentinel.application.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.init.InitArchitectureOption;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Stable choices and project-aware defaults used by the interactive init flow. */
public class InitGeneralCatalog {
  public static final String NO_INTEGRATION = "none";
  private final EnvironmentInspection environment;

  @Inject
  public InitGeneralCatalog(final EnvironmentInspection environment) {
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
    final boolean gradle = project.buildTool() == BuildTool.GRADLE;
    final String executable = project.buildExecutable();
    final String toolName = gradle ? "Gradle" : "Maven";
    final String systemExecutable = gradle ? "gradle" : "mvn";
    final boolean toolAvailable =
        project.wrapperAvailable()
            || environment.inspect(project.root()).hasExecutable(systemExecutable);
    final String availability =
        toolAvailable
            ? toolName + " command available: " + executable
            : toolName
                + " is unavailable; install "
                + toolName
                + " or add a "
                + toolName
                + " Wrapper before running this gate.";
    final List<InitGateOption> options = new ArrayList<>();
    options.add(
        InitGateOptionFactory.option(
            "tests", "Runs the project's tests.", toolAvailable, availability, executable, "test"));
    options.add(
        InitGateOptionFactory.option(
            "compile",
            "Checks that the project compiles.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "classes", "compile")));
    options.add(
        InitGateOptionFactory.option(
            "coverage",
            "Enforces minimum test coverage.",
            toolAvailable,
            availability + " (minimum coverage is configured in pom.xml.)",
            executable,
            task(gradle, "jacocoTestReport", "jacoco:check")));
    options.add(
        InitGateOptionFactory.option(
            "spotbugs",
            "Finds potential bugs through static analysis.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "spotbugsMain", "spotbugs:check")));
    options.add(
        InitGateOptionFactory.option(
            "checkstyle",
            "Checks source style and formatting rules.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "checkstyleMain", "checkstyle:check")));
    options.add(
        InitGateOptionFactory.option(
            "pmd",
            "Finds common Java design and implementation problems with PMD.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "pmdMain", "pmd:check")));
    options.add(
        InitGateOptionFactory.option(
            "sonar",
            "Checks code quality, smells, and duplication.",
            toolAvailable,
            availability,
            executable,
            "sonar"));
    options.add(
        InitGateOptionFactory.option(
            "dependency-check",
            "Scans dependencies for known CVEs.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "dependencyCheckAnalyze", "dependency-check:check")));
    options.add(
        InitGateOptionFactory.option(
            "archunit",
            "Checks architecture rules with ArchitectureTest.",
            toolAvailable,
            availability + " (runs only ArchitectureTest.)",
            executable,
            task(gradle, "--tests", "-Dtest=ArchitectureTest"),
            task(gradle, "*ArchitectureTest", "test")));
    options.add(
        InitGateOptionFactory.option(
            "mutation",
            "Measures test strength with mutation testing.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "pitest", "pitest:mutationCoverage")));
    options.add(
        InitGateOptionFactory.option(
            "format",
            "Checks formatting with Spotless and google-java-format.",
            toolAvailable,
            availability,
            executable,
            task(gradle, "spotlessCheck", "spotless:check")));
    options.add(
        InitGateOptionFactory.binaryOption(
            environment,
            project,
            "semgrep",
            "Runs SAST and custom security rules with Semgrep.",
            List.of("semgrep", "scan", "--config", "p/java", "--error"),
            "Install Semgrep (needs network access to fetch the p/java ruleset)."));
    options.add(
        InitGateOptionFactory.binaryOption(
            environment,
            project,
            "gitleaks",
            "Detects committed secrets with Gitleaks.",
            List.of("gitleaks", "detect", "--no-banner", "--redact"),
            "Install Gitleaks."));
    options.add(
        InitGateOptionFactory.binaryOption(
            environment,
            project,
            "zap",
            "Runs an OWASP ZAP baseline DAST scan against a running application.",
            List.of("zap-baseline.py", "-t", SupportedQualityGates.ZAP_TARGET_PLACEHOLDER),
            "Install ZAP and replace "
                + SupportedQualityGates.ZAP_TARGET_PLACEHOLDER
                + " with a target you are authorized to scan before running."));
    options.add(
        InitGateOptionFactory.binaryOption(
            environment,
            project,
            "trivy",
            "Scans dependencies, containers, and IaC with Trivy.",
            List.of("trivy", "fs", "--exit-code", "1", "."),
            "Install Trivy."));
    options.add(
        InitGateOptionFactory.option(
            "enforcer",
            "Enforces dependency and build rules with Maven Enforcer.",
            toolAvailable,
            availability + " (dependency and build rules are configured in pom.xml.)",
            executable,
            task(gradle, "check", "enforcer:enforce")));
    options.add(
        InitGateOptionFactory.option(
            "license",
            "Checks dependency license compliance with the License Maven Plugin.",
            toolAvailable,
            availability + " (license policy is configured in pom.xml.)",
            executable,
            task(gradle, "licenseMain", "license:add-third-party")));
    options.add(
        InitGateOptionFactory.option(
            "api-compat",
            "Checks binary/API compatibility between versions with japicmp.",
            toolAvailable,
            availability + " (the comparison baseline is configured in pom.xml.)",
            executable,
            task(gradle, "japicmp", "japicmp:cmp")));
    options.add(
        InitGateOptionFactory.option(
            "command",
            "Runs a custom command from sentinel.toml.",
            toolAvailable,
            availability + " (customize the command in sentinel.toml after initialization.)",
            executable,
            "test"));
    options.add(
        new InitGateOption(
            SupportedQualityGates.TRACEABILITY,
            "Maps live OpenSpec scenarios to explicitly annotated JUnit tests (opt-in).",
            List.of(),
            true,
            "Source-level mapping analysis; it does not execute tests."));
    return List.copyOf(options);
  }

  private static String task(
      final boolean gradle, final String gradleTask, final String mavenTask) {
    return gradle ? gradleTask : mavenTask;
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
    if ("openspec".equals(id) || "openspec-archived".equals(id)) {
      throw new SentinelException(
          "OpenSpec validations are tools, not quality gates. Use --tool openspec and --openspec-validation "
              + id
              + ".");
    }
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
}
