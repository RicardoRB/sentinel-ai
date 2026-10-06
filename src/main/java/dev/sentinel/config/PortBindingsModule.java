package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.RawTerminal;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.ProjectInspection;
import dev.sentinel.infrastructure.agent.ProcessAgentRunnerFactory;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.loop.GitStateInspector;
import dev.sentinel.infrastructure.process.ProcessCommandExecutor;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.infrastructure.terminal.PosixRawTerminal;

@Module
interface PortBindingsModule {
  @Binds
  CommandExecutor commandExecutor(ProcessCommandExecutor implementation);

  @Binds
  SentinelConfigurationReader configurationReader(TomlConfigurationReader implementation);

  @Binds
  ProjectInspection projectInspection(FileSystemProjectInspection implementation);

  @Binds
  EnvironmentInspection environmentInspection(SystemEnvironmentInspection implementation);

  @Binds
  GitStateInspection gitStateInspection(GitStateInspector implementation);

  @Binds
  AgentRunnerFactory agentRunnerFactory(ProcessAgentRunnerFactory implementation);

  @Binds
  ConfigurationStorage configurationStorage(FileConfigurationStorage implementation);

  @Binds
  RawTerminal rawTerminal(PosixRawTerminal implementation);

  @Binds
  BuildToolConfiguration buildToolConfiguration(PomToolConfigurator implementation);

  @Binds
  ArchitectureTestGeneration architectureTestGeneration(ArchitectureTestGenerator implementation);
}
