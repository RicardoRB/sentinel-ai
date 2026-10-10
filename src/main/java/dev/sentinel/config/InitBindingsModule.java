package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.RawTerminal;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.terminal.TerminalCapabilities;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
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
  BuildToolConfiguration buildToolConfiguration(PomToolConfigurator implementation);

  @Binds
  ArchitectureTestGeneration architectureTestGeneration(ArchitectureTestGenerator implementation);

  @Binds
  RuleFileGeneration ruleFileGeneration(PresetRuleFileWriter implementation);
}
