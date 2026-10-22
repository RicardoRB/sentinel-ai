package dev.ward.application.spec;

import dev.ward.application.gate.CheckService;
import dev.ward.application.gate.QualityGateFactory;
import dev.ward.application.gate.QualityGateRunner;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.spec.FileSystemProjectPaths;

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
