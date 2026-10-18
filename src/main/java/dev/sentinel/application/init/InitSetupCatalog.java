package dev.sentinel.application.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitToolOption;
import dev.sentinel.domain.init.QualityHookGeneration;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.inject.Inject;

/** Stable OpenSpec tool choices and project-aware defaults used by init. */
public final class InitSetupCatalog extends InitGeneralCatalog {
  private final EnvironmentInspection environment;
  private static final Logger LOGGER = Logger.getLogger(InitSetupCatalog.class.getName());

  private final Supplier<QualityHookGeneration> qualityHooks;
  private final SentinelConfigurationReader configurations;

  public InitSetupCatalog(final EnvironmentInspection environment) {
    this(environment, new NoQualityHookGeneration());
  }

  public InitSetupCatalog(
      final EnvironmentInspection environment, final QualityHookGeneration qualityHooks) {
    this(
        environment,
        qualityHooks,
        file -> {
          throw new SentinelException("Reading the existing configuration is unavailable.");
        });
  }

  @Inject
  public InitSetupCatalog(
      final EnvironmentInspection environment,
      final QualityHookGeneration qualityHooks,
      final SentinelConfigurationReader configurations) {
    super(environment);
    this.environment = environment;
    this.qualityHooks = () -> qualityHooks;
    this.configurations = configurations;
  }

  /**
   * The preset and offered quality gates recorded in the project's existing {@code sentinel.toml},
   * in declaration order, or empty when it cannot be read.
   */
  public Optional<PreviousChoices> previousChoices(final Project project) {
    final SentinelConfiguration configuration;
    try {
      configuration = configurations.read(project.root().resolve(SentinelConfiguration.FILE_NAME));
    } catch (SentinelException unreadable) {
      LOGGER.log(Level.INFO, "event=init-reconfigure-prefill status=unreadable");
      return Optional.empty();
    }
    final Set<String> offered =
        gates(project).stream().map(InitGateOption::id).collect(Collectors.toSet());
    return Optional.of(
        new PreviousChoices(
            configuration.preset() == null ? null : preset(configuration.preset()),
            configuration.enabledGates().keySet().stream().filter(offered::contains).toList()));
  }

  QualityHookGeneration qualityHooks() {
    return qualityHooks.get();
  }

  public IntegrationResult removeQualityHooks(final Path projectRoot) {
    return qualityHooks.get().remove(projectRoot);
  }

  public List<InitToolOption> tools() {
    return List.of(new InitToolOption("openspec", "OpenSpec project validation tool"));
  }

  public List<String> openSpecValidations() {
    return List.of("openspec", "openspec-archived");
  }

  public boolean openSpecAvailable(final Project project) {
    return environment.inspectExecutable(project.root(), "openspec").usable();
  }

  public boolean nvdApiKeySet(final Project project) {
    return environment.inspect(project.root()).isSet("NVD_API_KEY");
  }

  public InitGateOption openSpecValidation(
      final Project project, final String id, final String executable) {
    if (!openSpecValidations().contains(id)) {
      throw new SentinelException(
          "Unknown OpenSpec validation '"
              + id
              + "'. Supported validations: openspec, openspec-archived");
    }
    final List<String> command =
        "openspec".equals(id)
            ? List.of(executable, "validate", "--all", "--strict", "--no-interactive")
            : List.of(executable, "validate", "--archived", "--no-interactive");
    final boolean available = environment.inspectExecutable(project.root(), executable).usable();
    return new InitGateOption(
        id,
        "OpenSpec " + ("openspec".equals(id) ? "active" : "archived") + " validation.",
        command,
        available,
        available
            ? "OpenSpec executable available."
            : "OpenSpec is unavailable; install it or configure a local executable.");
  }
}
