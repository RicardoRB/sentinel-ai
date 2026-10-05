package dev.sentinel.config;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import dev.sentinel.application.CheckService;
import dev.sentinel.application.InitService;
import dev.sentinel.application.ProjectDetector;
import dev.sentinel.application.QualityGateFactory;
import dev.sentinel.application.QualityGateRunner;
import dev.sentinel.cli.CheckCommand;
import dev.sentinel.cli.CommandLineRunnerImpl;
import dev.sentinel.cli.DetectCommand;
import dev.sentinel.cli.InitCommand;
import dev.sentinel.cli.IntegrateCommand;
import dev.sentinel.cli.DoctorCommand;
import dev.sentinel.cli.LoopCommand;
import dev.sentinel.cli.JsonReportRenderer;
import dev.sentinel.cli.SentinelCommand;
import dev.sentinel.cli.TextReportRenderer;
import dev.sentinel.cli.VersionProvider;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.infrastructure.ProcessCommandExecutor;
import dev.sentinel.infrastructure.TomlConfigurationReader;

/** Explicit application wiring for the standalone CLI. */
public final class SentinelModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CommandExecutor.class).to(ProcessCommandExecutor.class);
        bind(SentinelConfigurationReader.class).to(TomlConfigurationReader.class);

        bind(ProjectDetector.class);
        bind(QualityGateFactory.class);
        bind(QualityGateRunner.class);
        bind(InitService.class);
        bind(CheckService.class);
        bind(dev.sentinel.application.IntegrationService.class);
        bind(dev.sentinel.application.OpenCodeIntegration.class);
        bind(dev.sentinel.application.ClaudeCodeIntegration.class);
        bind(dev.sentinel.application.DoctorService.class);

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

        bind(String.class).annotatedWith(Names.named("sentinel.version"))
                .toInstance(resolveVersion());
    }

    private static String resolveVersion() {
        String version = SentinelModule.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "dev" : version;
    }
}
