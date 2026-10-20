package dev.sentinel.application.spec;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.infrastructure.config.FilePathExistence;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.infrastructure.spec.FileSystemProjectPaths;

/** Wires real project detection, configuration, and paths around a test executor. */
public final class SpecValidationFixtures {
  private SpecValidationFixtures() {}

  /**
   * Returns a check service with real adapters, running commands through {@code executor} and
   * finding executables with {@code environment}.
   */
  public static CheckService checks(
      final CommandExecutor executor, final EnvironmentInspection environment) {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor, environment),
        new QualityGateRunner(),
        new FilePathExistence());
  }

  /**
   * Returns a spec validation service built on {@code checks}, running OpenSpec through {@code
   * executor}.
   */
  public static SpecValidationService service(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final CheckService checks) {
    return new SpecValidationService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new FileSystemProjectPaths(),
        new OpenSpecValidator(executor, environment),
        new ConfiguredSpecValidation(checks));
  }
}
