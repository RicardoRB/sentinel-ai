package dev.sentinel.application.gate;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.infrastructure.config.FilePathExistence;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;

/** A {@link CheckService} wired like production around a test {@link CommandExecutor}. */
public final class CheckServiceFixtures {
  private CheckServiceFixtures() {}

  public static CheckService checks(final CommandExecutor executor) {
    return new CheckService(
        new ProjectDetector(new FileSystemProjectInspection()),
        new TomlConfigurationReader(),
        new QualityGateFactory(executor),
        new QualityGateRunner(),
        new FilePathExistence());
  }
}
