package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.infrastructure.process.ProcessCommandExecutor;

@Module
abstract class ProcessBindingsModule {
  @Binds
  abstract CommandExecutor commandExecutor(ProcessCommandExecutor implementation);
}
