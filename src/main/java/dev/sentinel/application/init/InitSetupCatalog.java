package dev.sentinel.application.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitToolOption;
import dev.sentinel.domain.init.QualityHookGeneration;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Stable OpenSpec tool choices and project-aware defaults used by init. */
public final class InitSetupCatalog extends InitGeneralCatalog {
  private final EnvironmentInspection environment;
  private final Supplier<QualityHookGeneration> qualityHooks;

  public InitSetupCatalog(final EnvironmentInspection environment) {
    this(environment, new NoQualityHookGeneration());
  }

  @Inject
  public InitSetupCatalog(
      final EnvironmentInspection environment, final QualityHookGeneration qualityHooks) {
    super(environment);
    this.environment = environment;
    this.qualityHooks = () -> qualityHooks;
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
