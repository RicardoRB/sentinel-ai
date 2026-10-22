package dev.ward.application.gate;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;

/** A {@link CheckService} wired like production around a test {@link CommandExecutor}. */
public final class CheckServiceFixtures {
  private CheckServiceFixtures() {}

  /**
   * Returns a check service with real adapters that runs gate commands through {@code executor}.
   */
  public static CheckService checks(final CommandExecutor executor) {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor),
        new QualityGateRunner(),
        new FilePathExistence());
  }
}
