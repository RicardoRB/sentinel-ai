package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dev.sentinel.application.init.InitProjectFiles;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.OwnedSectionGeneration;
import dev.sentinel.domain.init.QualityHookGeneration;
import dev.sentinel.domain.init.RawTerminal;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.domain.terminal.TerminalCapabilities;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.BuildToolConfigurator;
import dev.sentinel.infrastructure.init.CompositeToolInstaller;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.OwnedSectionWriter;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.init.QualityHookInstaller;
import dev.sentinel.infrastructure.terminal.JdkTerminalCapabilities;
import dev.sentinel.infrastructure.terminal.PosixRawTerminal;

@Module
interface InitBindingsModule {
  @Binds
  ConfigurationStorage configurationStorage(FileConfigurationStorage implementation);

  @Binds
  RawTerminal rawTerminal(PosixRawTerminal implementation);

  @Binds
  TerminalCapabilities terminalCapabilities(JdkTerminalCapabilities implementation);

  @Binds
  BuildToolConfiguration buildToolConfiguration(BuildToolConfigurator implementation);

  @Binds
  ArchitectureTestGeneration architectureTestGeneration(ArchitectureTestGenerator implementation);

  @Binds
  RuleFileGeneration ruleFileGeneration(PresetRuleFileWriter implementation);

  @Binds
  QualityHookGeneration qualityHookGeneration(QualityHookInstaller implementation);

  @Binds
  OwnedSectionGeneration ownedSectionGeneration(OwnedSectionWriter implementation);

  @Binds
  ToolInstaller toolInstaller(CompositeToolInstaller implementation);

  @Provides
  static InitProjectFiles initProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration,
      final OwnedSectionGeneration sections) {
    return new InitProjectFiles(buildTools, ruleFiles, architectureTests, configuration, sections);
  }
}
