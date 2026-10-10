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
import java.util.LinkedHashSet;
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
  private final IntegrationInstaller installer;
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
    this.installer = new IntegrationInstaller(integrations, catalog);
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
    installer.validate(selection.integrations());
    final List<InitGateOption> gates = resolveGates(project, selection);
    if (selection.architecture() != null) {
      catalog.architecture(selection.architecture());
    }
    final Path configurationFile = project.root().resolve(SentinelConfiguration.FILE_NAME);
    final String originalConfiguration = configurationStorage.read(configurationFile).orElse(null);
    if (originalConfiguration != null && !overwrite) {
      return new InitResult(configurationFile, false, gates, List.of());
    }
    final AppliedChanges changes = new AppliedChanges(project.root(), selection.integrations());
    boolean completed = false;
    try {
      apply(project, selection, gates, changes);
      final InitResult result =
          writeConfiguration(configurationFile, gates, selection.preset(), changes, overwrite);
      if (!result.created()) {
        changes.rollback();
      }
      completed = true;
      return result;
    } finally {
      if (!completed) {
        changes.rollback();
        if (originalConfiguration != null) {
          restoreConfiguration(configurationFile, originalConfiguration);
        }
      }
    }
  }

  /** Returns the preset's gates followed by any additionally selected gates, without duplicates. */
  private List<InitGateOption> resolveGates(final Project project, final InitSelection selection) {
    final Set<String> gateIds = new LinkedHashSet<>();
    if (selection.preset() != null) {
      gateIds.addAll(selection.preset().gates());
    }
    gateIds.addAll(selection.gates());
    if (gateIds.isEmpty()) {
      throw new SentinelException("Select at least one quality gate.");
    }
    return gateIds.stream().map(id -> catalog.gate(project, id)).toList();
  }

  private void apply(
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates,
      final AppliedChanges changes) {
    final QualityPreset preset = selection.preset();
    installer.install(project.root(), selection.integrations(), changes.installed);
    changes.pomChange = pomTools.apply(project, gates, preset);
    changes.ruleFileChange = ruleFiles.apply(project, preset, gates);
    if (gates.stream().anyMatch(gate -> "archunit".equals(gate.id()))) {
      changes.architectureTest =
          architectureTests.apply(
              project,
              selection.architecture() == null ? "layered" : selection.architecture(),
              preset == null
                  ? new QualityPreset.ArchitectureExtras(false, false, false)
                  : preset.rules().architectureExtras());
    }
  }

  private InitResult writeConfiguration(
      final Path file,
      final List<InitGateOption> gates,
      final QualityPreset preset,
      final AppliedChanges changes,
      final boolean overwrite) {
    final String content = ConfigurationTemplate.render(gates, preset);
    final boolean created;
    if (overwrite) {
      configurationStorage.replace(file, content);
      created = true;
    } else {
      created = configurationStorage.create(file, content);
    }
    return new InitResult(
        file,
        created,
        overwrite,
        gates,
        changes.installed,
        changes.pomChange.tools(),
        changes.architectureTest,
        changes.ruleFileChange == null ? List.of() : changes.ruleFileChange.preserved(),
        changes.pomChange.warnings());
  }

  // Cleanup is best effort: any failure here must not hide the original setup failure.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private void restoreConfiguration(final Path configurationFile, final String original) {
    try {
      configurationStorage.restore(configurationFile, original);
    } catch (RuntimeException ignored) {
      // Restoration can be retried manually.
    }
  }

  /** Files changed by one initialization, so they can be rolled back together. */
  private final class AppliedChanges {
    private final Path root;
    private final List<String> integrationIds;
    private final List<IntegrationResult> installed = new ArrayList<>();
    private PomChange pomChange;
    private RuleFileChange ruleFileChange;
    private ArchitectureTestChange architectureTest;

    AppliedChanges(final Path root, final List<String> integrationIds) {
      this.root = root;
      this.integrationIds = integrationIds;
    }

    void rollback() {
      architectureTests.rollback(architectureTest);
      ruleFiles.rollback(ruleFileChange);
      pomTools.rollback(pomChange);
      installer.rollbackNew(root, integrationIds, installed);
    }
  }
}
