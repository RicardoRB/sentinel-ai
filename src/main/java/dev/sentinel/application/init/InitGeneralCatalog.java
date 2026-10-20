package dev.sentinel.application.init;

import dev.sentinel.domain.agent.SupportedAgents;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.init.GateToolInstallCatalog;
import dev.sentinel.domain.init.InitArchitectureOption;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.init.PlannedGateInstallation;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Stable choices and project-aware defaults used by the interactive init flow. */
public class InitGeneralCatalog {
  public static final String NO_INTEGRATION = "none";
  private final EnvironmentInspection environment;

  /** Creates the catalog; {@code environment} resolves which executables are on the path. */
  @Inject
  public InitGeneralCatalog(final EnvironmentInspection environment) {
    this.environment = environment;
  }

  /** Returns the agent integration choices, starting with the option to install none. */
  public List<InitIntegrationOption> integrations() {
    final List<InitIntegrationOption> options = new ArrayList<>();
    options.add(new InitIntegrationOption(NO_INTEGRATION, "No agent integration"));
    SupportedAgents.definitions().stream()
        .map(definition -> new InitIntegrationOption(definition.id(), definition.label()))
        .forEach(options::add);
    return List.copyOf(options);
  }

  /** Returns the supported architecture styles for the generated ArchUnit test. */
  public List<InitArchitectureOption> architectures() {
    return List.of(
        new InitArchitectureOption("layered", "Layered (controller -> service -> repository)"),
        new InitArchitectureOption("hexagonal", "Hexagonal (domain isolated from adapters)"),
        new InitArchitectureOption("clean", "Clean (domain isolated from outer layers)"));
  }

  /**
   * Returns the architecture style with {@code id}.
   *
   * @throws SentinelException if {@code id} is not a supported style
   */
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

  /**
   * Returns every gate choice for {@code project}, in wizard order, with availability resolved
   * against the project's build tool and the executables on the path.
   */
  public List<InitGateOption> gates(final Project project) {
    final BuildToolGates buildTool = buildToolGates(project);
    final List<InitGateOption> options = new ArrayList<>(buildTool.analysisGates());
    options.addAll(new BinaryToolGates(environment, project).gates());
    options.addAll(buildTool.policyGates());
    options.add(
        new InitGateOption(
            SupportedQualityGates.TRACEABILITY,
            "Maps live OpenSpec scenarios to explicitly annotated JUnit tests (opt-in).",
            List.of(),
            true,
            "Source-level mapping analysis; it does not execute tests."));
    return List.copyOf(options);
  }

  private BuildToolGates buildToolGates(final Project project) {
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
    return new BuildToolGates(gradle, toolAvailable, availability, executable);
  }

  /**
   * Returns the agent integration with {@code id}.
   *
   * @throws SentinelException if {@code id} is not a supported integration
   */
  public InitIntegrationOption integration(final String id) {
    return integrations().stream()
        .filter(option -> option.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown integration '"
                        + id
                        + "'. Supported integrations: none, "
                        + SupportedAgents.idsText()));
  }

  /**
   * Returns the gate choice with {@code id} for {@code project}.
   *
   * @throws SentinelException if {@code id} is an OpenSpec tool or not a supported gate
   */
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

  /**
   * Plans how to install the executable a missing {@code gate} needs, preferring the first package
   * manager found on the path; empty when the gate is available or runs no command.
   */
  public Optional<PlannedGateInstallation> gateInstallation(
      final Project project, final InitGateOption gate) {
    if (gate.available() || gate.command().isEmpty()) {
      return Optional.empty();
    }
    final String executable = gate.command().getFirst();
    return GateToolInstallCatalog.forExecutable(executable)
        .map(
            entry ->
                entry.methods().stream()
                    .filter(
                        method ->
                            environment.inspect(project.root()).hasExecutable(method.manager()))
                    .findFirst()
                    .map(
                        method ->
                            new PlannedGateInstallation(
                                executable,
                                method.manager(),
                                method.arguments(),
                                entry.manualGuidance()))
                    .orElseGet(
                        () ->
                            new PlannedGateInstallation(
                                executable, null, List.of(), entry.manualGuidance())));
  }

  /** Returns the quality presets init can apply. */
  public List<QualityPreset> presets() {
    return List.of(QualityPreset.STANDARD, QualityPreset.STRICT);
  }

  /**
   * Returns the quality preset with {@code id}.
   *
   * @throws SentinelException if {@code id} is not a supported preset
   */
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
