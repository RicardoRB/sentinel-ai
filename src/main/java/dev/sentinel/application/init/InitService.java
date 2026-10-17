package dev.sentinel.application.init;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.InitPlan;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

public class InitService {
  private static final Logger LOGGER = Logger.getLogger(InitService.class.getName());

  static final String DEFAULT_CONFIGURATION =
      """
            version = 1

            [quality-gates.tests]
            enabled = true
            command = "./mvnw test"
            """;

  private final ProjectDetector detector;
  private final IntegrationInstaller installer;
  private final Supplier<InitSetupCatalog> catalog;
  private final BuildToolConfiguration pomTools;
  private final ArchitectureTestGeneration architectureTests;
  private final Supplier<ConfigurationStorage> configurationStorage;
  private final RuleFileGeneration ruleFiles;
  private final ToolInstaller toolInstaller;

  @Inject
  public InitService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final InitSetupCatalog catalog,
      final BuildToolConfiguration pomTools,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configurationStorage,
      final RuleFileGeneration ruleFiles,
      final ToolInstaller toolInstaller) {
    this.detector = detector;
    this.installer = new IntegrationInstaller(integrations, catalog);
    this.catalog = () -> catalog;
    this.pomTools = pomTools;
    this.architectureTests = architectureTests;
    this.configurationStorage = () -> configurationStorage;
    this.ruleFiles = ruleFiles;
    this.toolInstaller = toolInstaller;
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
        new NoRuleFileGeneration(),
        new UnavailableToolInstaller());
  }

  public InitService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final InitSetupCatalog catalog,
      final BuildToolConfiguration pomTools,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configurationStorage,
      final RuleFileGeneration ruleFiles) {
    this(
        detector,
        integrations,
        catalog,
        pomTools,
        architectureTests,
        configurationStorage,
        ruleFiles,
        new UnavailableToolInstaller());
  }

  public InitResult init(final Path start) {
    final Project project = project(start);
    final Path file = project.root().resolve("sentinel.toml");
    final String executable = project.buildExecutable();
    final String configuration = DEFAULT_CONFIGURATION.replace("./mvnw test", executable + " test");
    return new InitResult(file, configurationStorage.get().create(file, configuration));
  }

  public Project project(final Path start) {
    return detector.detect(start).orElseThrow(ProjectNotFoundException::new);
  }

  public boolean configurationExists(final Project project) {
    return configurationStorage.get().exists(project.root().resolve("sentinel.toml"));
  }

  public InitSetupCatalog catalog() {
    return catalog.get();
  }

  public InitResult initialize(final Path start, final InitSelection selection) {
    return initialize(start, selection, false, new Object());
  }

  public InitResult initialize(
      final Path start,
      final InitSelection selection,
      final boolean overwrite,
      final Object approval) {
    LOGGER.log(Level.INFO, () -> "event=initialization-start overwrite=" + overwrite);
    final Project project = project(start);
    installer.validate(selection.integrations());
    final Path configurationFile = project.root().resolve("sentinel.toml");
    final String originalConfiguration =
        configurationStorage.get().read(configurationFile).orElse(null);
    if (originalConfiguration != null && !overwrite) {
      LOGGER.log(Level.INFO, "event=initialization-complete status=ownership-conflict");
      return new InitResult(configurationFile, false, List.of(), List.of());
    }
    final Path installed = OpenSpecInstallation.install(toolInstaller, project, selection);
    final String executable = OpenSpecInstallation.executable(installed);
    final List gates = SelectedGates.resolve(catalog.get(), project, selection, executable);
    if (selection.architecture() != null) {
      catalog.get().architecture(selection.architecture());
    }
    final InitChanges changes =
        new InitChanges(
            project.root(),
            selection.integrations(),
            architectureTests,
            ruleFiles,
            pomTools,
            installer,
            catalog.get().qualityHooks());
    boolean completed = false;
    try {
      changes.apply(project, selection, gates, approval);
      final InitResult result =
          writeConfiguration(configurationFile, gates, selection.preset(), changes, overwrite);
      if (!result.created()) {
        changes.rollback();
      }
      completed = true;
      LOGGER.log(Level.INFO, "event=initialization-complete status=applied");
      return result;
    } finally {
      if (!completed) {
        toolInstaller.rollbackPath(project.root(), installed);
        changes.rollback();
        LOGGER.log(Level.WARNING, "event=initialization-rollback status=applied");
        if (originalConfiguration != null) {
          restoreConfiguration(configurationFile, originalConfiguration);
        }
      }
    }
  }

  /**
   * Describes everything {@link #initialize} would change for this selection, in the order it
   * applies changes, without writing files, installing tools, or asking for approval. {@code
   * approved} marks compatibility updates as already approved.
   */
  public InitPlan plan(final Path start, final InitSelection selection, final boolean approved) {
    return new InitPlanner(
            catalog, installer, pomTools, ruleFiles, architectureTests, configurationStorage)
        .plan(project(start), selection, approved);
  }

  private InitResult writeConfiguration(
      final Path file,
      final List gates,
      final QualityPreset preset,
      final InitChanges changes,
      final boolean overwrite) {
    final String content = ConfigurationTemplate.render(gates, preset);
    final boolean created;
    if (overwrite) {
      configurationStorage.get().replace(file, content);
      created = true;
    } else {
      created = configurationStorage.get().create(file, content);
    }
    return changes.result(file, created, overwrite, gates);
  }

  // Cleanup is best effort: any failure here must not hide the original setup failure.
  private void restoreConfiguration(final Path configurationFile, final String original) {
    try {
      configurationStorage.get().restore(configurationFile, original);
    } catch (IllegalStateException ignored) {
      // Restoration can be retried manually.
    }
  }
}
