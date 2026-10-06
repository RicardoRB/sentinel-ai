package dev.sentinel.config;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.application.doctor.DoctorService;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.loop.QualityLoopService;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.agent.AgentIntegration;
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
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import dev.sentinel.infrastructure.agent.ProcessAgentRunnerFactory;
import dev.sentinel.infrastructure.cli.CommandLineRunnerImpl;
import dev.sentinel.infrastructure.cli.SentinelCommand;
import dev.sentinel.infrastructure.cli.VersionProvider;
import dev.sentinel.infrastructure.cli.agent.IntegrateCommand;
import dev.sentinel.infrastructure.cli.doctor.DoctorCommand;
import dev.sentinel.infrastructure.cli.gate.CheckCommand;
import dev.sentinel.infrastructure.cli.gate.JsonReportRenderer;
import dev.sentinel.infrastructure.cli.gate.TextReportRenderer;
import dev.sentinel.infrastructure.cli.init.InitCommand;
import dev.sentinel.infrastructure.cli.loop.LoopCommand;
import dev.sentinel.infrastructure.cli.project.DetectCommand;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.loop.GitStateInspector;
import dev.sentinel.infrastructure.process.ProcessCommandExecutor;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.infrastructure.terminal.PosixRawTerminal;
import java.util.Set;

/** Explicit application wiring for the standalone CLI. */
public final class SentinelModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(CommandExecutor.class).to(ProcessCommandExecutor.class);
    bind(SentinelConfigurationReader.class).to(TomlConfigurationReader.class);
    bind(ProjectInspection.class).to(FileSystemProjectInspection.class);
    bind(EnvironmentInspection.class).to(SystemEnvironmentInspection.class);
    bind(GitStateInspection.class).to(GitStateInspector.class);
    bind(AgentRunnerFactory.class).to(ProcessAgentRunnerFactory.class);
    bind(ConfigurationStorage.class).to(FileConfigurationStorage.class);
    bind(RawTerminal.class).to(PosixRawTerminal.class);
    bind(BuildToolConfiguration.class).to(PomToolConfigurator.class);
    bind(ArchitectureTestGeneration.class).to(ArchitectureTestGenerator.class);

    bind(ProjectDetector.class);
    bind(InitSetupCatalog.class);
    bind(QualityGateFactory.class);
    bind(QualityGateRunner.class);
    bind(InitService.class);
    bind(CheckService.class);
    bind(QualityLoopService.class);
    bind(IntegrationService.class);
    bind(integrationSetKey())
        .toInstance(Set.of(new OpenCodeIntegration(), new ClaudeCodeIntegration()));
    bind(DoctorService.class);

    bind(SentinelCommand.class);
    bind(DetectCommand.class);
    bind(InitCommand.class);
    bind(CheckCommand.class);
    bind(IntegrateCommand.class);
    bind(DoctorCommand.class);
    bind(LoopCommand.class);
    bind(TextReportRenderer.class);
    bind(JsonReportRenderer.class);
    bind(VersionProvider.class);
    bind(CommandLineRunnerImpl.class);

    bind(String.class).annotatedWith(Names.named("sentinel.version")).toInstance(resolveVersion());
  }

  private static String resolveVersion() {
    String version = SentinelModule.class.getPackage().getImplementationVersion();
    return version == null || version.isBlank() ? "dev" : version;
  }

  @SuppressWarnings("unchecked")
  private static Key<Set<AgentIntegration>> integrationSetKey() {
    return (Key<Set<AgentIntegration>>) (Key<?>) Key.get(Types.setOf(AgentIntegration.class));
  }
}
