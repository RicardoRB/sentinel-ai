package dev.sentinel.application.init;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

public class InitService {

  static final String DEFAULT_CONFIGURATION =
      """
            version = 1

            [quality-gates.tests]
            enabled = true
            command = "./mvnw test"
            """;

  private final ProjectDetector detector;
  private final Set<AgentIntegration> integrations;
  private final InitSetupCatalog catalog;
  private final BuildToolConfiguration pomTools;
  private final ArchitectureTestGeneration architectureTests;
  private final ConfigurationStorage configurationStorage;
  private final RuleFileGeneration ruleFiles;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "The constructor-injected storage port is retained for use-case orchestration.")
  public InitService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final InitSetupCatalog catalog,
      final BuildToolConfiguration pomTools,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configurationStorage,
      final RuleFileGeneration ruleFiles) {
    this.detector = detector;
    this.integrations = Set.copyOf(integrations);
    this.catalog = catalog;
    this.pomTools = pomTools;
    this.architectureTests = architectureTests;
    this.configurationStorage = configurationStorage;
    this.ruleFiles = ruleFiles;
  }

  public InitService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final InitSetupCatalog catalog,
      final BuildToolConfiguration pomTools,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configurationStorage) {
    this(
        detector,
        integrations,
        catalog,
        pomTools,
        architectureTests,
        configurationStorage,
        new RuleFileGeneration() {
          @Override
          public RuleFileChange apply(
              final Project p, final QualityPreset q, final List<InitGateOption> g) {
            return new RuleFileChange(p.root().resolve("config"), false, Map.of(), List.of());
          }

          @Override
          public void rollback(final RuleFileChange c) {
            // Nothing to restore for the compatibility no-op port.
          }
        });
  }

  public InitResult init(final Path start) {
    final Project project = project(start);
    final Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
    return new InitResult(file, configurationStorage.create(file, DEFAULT_CONFIGURATION));
  }

  public Project project(final Path start) {
    return detector.detect(start).orElseThrow(ProjectNotFoundException::new);
  }

  public boolean configurationExists(final Project project) {
    return configurationStorage.exists(project.root().resolve(SentinelConfiguration.FILE_NAME));
  }

  public InitSetupCatalog catalog() {
    return catalog;
  }

  public InitResult initialize(final Path start, final InitSelection selection) {
    return initialize(start, selection, false);
  }

  public InitResult initialize(
      final Path start, final InitSelection selection, final boolean overwrite) {
    final Project project = project(start);
    validateIntegrations(selection.integrations());
    final QualityPreset preset = selection.preset();
    final List<String> gateIds = new ArrayList<>();
    if (preset != null) {
      gateIds.addAll(preset.gates());
    }
    selection
        .gates()
        .forEach(
            id -> {
              if (!gateIds.contains(id)) {
                gateIds.add(id);
              }
            });
    final List<InitGateOption> gates =
        gateIds.stream().map(id -> catalog.gate(project, id)).toList();
    if (gates.isEmpty()) {
      throw new SentinelException("Select at least one quality gate.");
    }
    if (selection.architecture() != null) {
      catalog.architecture(selection.architecture());
    }
    final Path configurationFile = project.root().resolve(SentinelConfiguration.FILE_NAME);
    final String originalConfiguration = configurationStorage.read(configurationFile).orElse(null);
    final boolean hadConfiguration = originalConfiguration != null;
    if (hadConfiguration && !overwrite) {
      return new InitResult(
          project.root().resolve(SentinelConfiguration.FILE_NAME), false, gates, List.of());
    }

    final List<IntegrationResult> installed = new ArrayList<>();
    PomChange pomChange = null;
    RuleFileChange ruleFileChange = null;
    ArchitectureTestChange architectureTest = null;
    try {
      for (final String integration : selection.integrations()) {
        if (InitSetupCatalog.NO_INTEGRATION.equals(integration)) {
          continue;
        }
        final IntegrationResult result = installIntegration(project.root(), integration);
        installed.add(result);
        if (result.status() == IntegrationResult.Status.CONFLICT) {
          throw new SentinelException(
              "Cannot initialize "
                  + project.root()
                  + ": selected integration conflicts with user-owned content.");
        }
      }
      pomChange = pomTools.apply(project, gates, preset);
      ruleFileChange = ruleFiles.apply(project, preset, gates);
      if (requiresArchitectureTest(gates)) {
        architectureTest =
            architectureTests.apply(
                project,
                selection.architecture() == null ? "layered" : selection.architecture(),
                preset == null
                    ? new QualityPreset.ArchitectureExtras(false, false, false)
                    : preset.rules().architectureExtras());
      }
      final InitResult result =
          writeConfiguration(
              project,
              gates,
              installed,
              pomChange,
              architectureTest,
              ruleFileChange,
              preset,
              overwrite);
      if (!result.created()) {
        ruleFiles.rollback(ruleFileChange);
        architectureTests.rollback(architectureTest);
        pomTools.rollback(pomChange);
        rollbackNewIntegrations(project.root(), selection.integrations(), installed);
      }
      return result;
    } catch (RuntimeException e) {
      architectureTests.rollback(architectureTest);
      ruleFiles.rollback(ruleFileChange);
      pomTools.rollback(pomChange);
      if (originalConfiguration != null) {
        try {
          configurationStorage.restore(configurationFile, originalConfiguration);
        } catch (RuntimeException ignored) {
          // Preserve the original setup failure; restoration can be retried manually.
        }
      }
      rollbackNewIntegrations(project.root(), selection.integrations(), installed);
      throw e;
    }
  }

  private InitResult writeConfiguration(
      final Project project,
      final List<InitGateOption> gates,
      final List<IntegrationResult> integrations,
      final PomChange pomChange,
      final ArchitectureTestChange architectureTest,
      final RuleFileChange ruleFileChange,
      final QualityPreset preset,
      final boolean overwrite) {
    final Path file = project.root().resolve(SentinelConfiguration.FILE_NAME);
    final String content = configuration(gates, preset);
    if (overwrite) {
      configurationStorage.replace(file, content);
      return new InitResult(
          file,
          true,
          true,
          gates,
          integrations,
          pomChange.tools(),
          architectureTest,
          ruleFileChange == null ? List.of() : ruleFileChange.preserved(),
          pomChange.warnings());
    }
    final boolean created = configurationStorage.create(file, content);
    return new InitResult(
        file,
        created,
        false,
        gates,
        integrations,
        pomChange.tools(),
        architectureTest,
        ruleFileChange == null ? List.of() : ruleFileChange.preserved(),
        pomChange.warnings());
  }

  private IntegrationResult installIntegration(final Path root, final String id) {
    if (integrations.isEmpty()) {
      throw new SentinelException("Agent integrations are unavailable in this runtime.");
    }
    return integration(id).integrate(root, false);
  }

  private void rollbackNewIntegrations(
      final Path root, final List<String> ids, final List<IntegrationResult> results) {
    for (int i = 0; i < Math.min(ids.size(), results.size()); i++) {
      if (results.get(i).status() != IntegrationResult.Status.CHANGED) {
        continue;
      }
      try {
        integration(ids.get(i)).integrate(root, true);
      } catch (RuntimeException ignored) {
        // Preserve the original initialization error; ownership-safe removal remains available.
      }
    }
  }

  private AgentIntegration integration(final String id) {
    return integrations.stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown agent '" + id + "'. Supported agents: opencode, claude-code"));
  }

  private void validateIntegrations(final List<String> ids) {
    if (ids.isEmpty()) {
      throw new SentinelException("Select at least one integration, or choose none.");
    }
    final boolean noIntegration = ids.contains(InitSetupCatalog.NO_INTEGRATION);
    if (noIntegration && ids.size() > 1) {
      throw new SentinelException(
          "The no-integration choice cannot be combined with another integration.");
    }
    ids.forEach(catalog::integration);
  }

  private static boolean requiresArchitectureTest(final List<InitGateOption> gates) {
    return gates.stream().anyMatch(gate -> "archunit".equals(gate.id()));
  }

  private static String configuration(
      final List<InitGateOption> gates, final QualityPreset preset) {
    final StringBuilder content = new StringBuilder("version = 1\n");
    if (preset != null) {
      content.append("preset = \"").append(preset.id()).append("\"\n");
    }
    for (final InitGateOption gate : gates) {
      final String executable =
          gate.command().getFirst().replace("\\", "\\\\").replace("\"", "\\\"");
      final String goal =
          gate.command().size() > 1
              ? gate.command().subList(1, gate.command().size()).stream()
                  .map(value -> value.replace("\\", "\\\\").replace("\"", "\\\""))
                  .reduce((left, right) -> left + " " + right)
                  .orElse("")
              : "";
      content
          .append("\n[quality-gates.")
          .append(gate.id())
          .append("]\nenabled = true\ncommand = \"")
          .append(executable)
          .append(" ")
          .append(goal)
          .append("\"\n");
    }
    return content.toString();
  }
}
