package dev.sentinel.application.init;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.init.CompatibilityFinding;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitPlan;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Initializes Sentinel in a project: plans and writes {@code sentinel.toml}, build plugins, rule
 * files, hooks, and integrations, rolling back on failure.
 */
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
  private final InitProjectFiles files;
  private final ToolInstaller toolInstaller;

  /**
   * Creates the service. {@code integrations} are the installable agent integrations, {@code files}
   * writes the project files, and {@code toolInstaller} installs optional tools.
   */
  @Inject
  public InitService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final InitSetupCatalog catalog,
      final InitProjectFiles files,
      final ToolInstaller toolInstaller) {
    this.detector = detector;
    this.installer = new IntegrationInstaller(integrations, catalog);
    this.catalog = () -> catalog;
    this.files = files;
    this.toolInstaller = toolInstaller;
  }

  /** Creates a service that cannot install optional tools. */
  public InitService(
      final ProjectDetector detector,
      final Set<AgentIntegration> integrations,
      final InitSetupCatalog catalog,
      final InitProjectFiles files) {
    this(detector, integrations, catalog, files, new UnavailableToolInstaller());
  }

  /** Creates a minimal {@code sentinel.toml} with only the tests gate. */
  public InitResult init(final Path start) {
    final Project project = project(start);
    final Path file = project.root().resolve("sentinel.toml");
    final String executable = project.buildExecutable();
    final String configuration = DEFAULT_CONFIGURATION.replace("./mvnw test", executable + " test");
    return new InitResult(file, files.configuration().create(file, configuration));
  }

  /**
   * Returns the project containing {@code start}.
   *
   * @throws ProjectNotFoundException if no supported project is found
   */
  public Project project(final Path start) {
    return detector.detect(start).orElseThrow(ProjectNotFoundException::new);
  }

  /** Returns whether {@code project} already has a {@code sentinel.toml}. */
  public boolean configurationExists(final Project project) {
    return files.configuration().exists(project.root().resolve("sentinel.toml"));
  }

  /** Returns the setup choices offered by init. */
  public InitSetupCatalog catalog() {
    return catalog.get();
  }

  /**
   * Applies {@code selection} without overwriting an existing configuration or approving plugin
   * updates.
   */
  public InitResult initialize(final Path start, final InitSelection selection) {
    return initialize(start, selection, false, finding -> false);
  }

  /**
   * Applies {@code selection}, overwriting an existing configuration when {@code overwrite} and
   * updating plugins that {@code approval} accepts. Every change is rolled back if a step fails.
   */
  public InitResult initialize(
      final Path start,
      final InitSelection selection,
      final boolean overwrite,
      final Predicate<? super CompatibilityFinding> approval) {
    LOGGER.log(Level.INFO, () -> "event=initialization-start overwrite=" + overwrite);
    final Project project = project(start);
    installer.validate(selection.integrations());
    final Path configurationFile = project.root().resolve("sentinel.toml");
    final String originalConfiguration = files.configuration().read(configurationFile).orElse(null);
    if (originalConfiguration != null && !overwrite) {
      LOGGER.log(Level.INFO, "event=initialization-complete status=ownership-conflict");
      return new InitResult(configurationFile, false, List.of(), List.of());
    }
    final Path installed = OpenSpecInstallation.install(toolInstaller, project, selection);
    final String executable = OpenSpecInstallation.executable(installed);
    final List<InitGateOption> gates =
        ArchUnitGateReuse.rewrite(
            project,
            SelectedGates.resolve(catalog.get(), project, selection, executable),
            files.architectureTests().existingArchUnitTests(project));
    if (selection.architecture() != null) {
      catalog.get().architecture(selection.architecture());
    }
    final InitChanges changes =
        new InitChanges(project.root(), selection.integrations(), files, installer, catalog.get());
    boolean completed = false;
    try {
      changes.apply(project, selection, gates, approval);
      final InitResult result = writeConfiguration(project, gates, selection, changes, overwrite);
      if (!result.created()) {
        changes.rollback();
      }
      if (result.created()) {
        GateToolInstallation.run(toolInstaller, project, selection, gates);
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
    return new InitPlanner(catalog, installer, files).plan(project(start), selection, approved);
  }

  private InitResult writeConfiguration(
      final Project project,
      final List<InitGateOption> gates,
      final InitSelection selection,
      final InitChanges changes,
      final boolean overwrite) {
    final Path file = project.root().resolve("sentinel.toml");
    final String content = ConfigurationTemplate.render(gates, selection.preset(), project);
    final boolean created;
    if (overwrite) {
      files.configuration().replace(file, content);
      created = true;
    } else {
      created = files.configuration().create(file, content);
    }
    return changes.result(
        file, created, overwrite, gates, catalog.get().openSpecWarnings(project, gates));
  }

  // Cleanup is best effort: any failure here must not hide the original setup failure.
  private void restoreConfiguration(final Path configurationFile, final String original) {
    try {
      files.configuration().restore(configurationFile, original);
    } catch (IllegalStateException ignored) {
      // Restoration can be retried manually.
    }
  }
}
