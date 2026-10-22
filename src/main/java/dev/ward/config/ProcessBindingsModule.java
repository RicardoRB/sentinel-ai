package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.infrastructure.process.ProcessCommandExecutor;

@Module
abstract class ProcessBindingsModule {
  @Binds
  abstract CommandExecutor commandExecutor(ProcessCommandExecutor implementation);
}
