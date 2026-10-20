package dev.sentinel.infrastructure.cli;

import dev.sentinel.application.init.InitProjectFiles;
import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.util.Set;

/** Real-adapter init services for CLI integration tests. */
final class CliInitServices {
  private CliInitServices() {}

  /** An init service offering the OpenCode and Claude Code integrations, without rule files. */
  static InitService openCodeAndClaude() {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(new OpenCodeIntegration(), new ClaudeCodeIntegration()),
        new InitSetupCatalog(new SystemEnvironmentInspection()),
        new InitProjectFiles(
            new PomToolConfigurator(),
            new ArchitectureTestGenerator(),
            new FileConfigurationStorage()));
  }
}
