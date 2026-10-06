package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.sentinel.infrastructure.cli.SentinelCommand;
import dev.sentinel.infrastructure.cli.agent.IntegrateCommand;
import dev.sentinel.infrastructure.cli.doctor.DoctorCommand;
import dev.sentinel.infrastructure.cli.gate.CheckCommand;
import dev.sentinel.infrastructure.cli.init.InitCommand;
import dev.sentinel.infrastructure.cli.loop.LoopCommand;
import dev.sentinel.infrastructure.cli.project.DetectCommand;

@Module
interface CommandBindingsModule {
  @Binds
  @IntoMap
  @ClassKey(SentinelCommand.class)
  Object sentinelCommand(SentinelCommand command);

  @Binds
  @IntoMap
  @ClassKey(DetectCommand.class)
  Object detectCommand(DetectCommand command);

  @Binds
  @IntoMap
  @ClassKey(InitCommand.class)
  Object initCommand(InitCommand command);

  @Binds
  @IntoMap
  @ClassKey(CheckCommand.class)
  Object checkCommand(CheckCommand command);

  @Binds
  @IntoMap
  @ClassKey(IntegrateCommand.class)
  Object integrateCommand(IntegrateCommand command);

  @Binds
  @IntoMap
  @ClassKey(DoctorCommand.class)
  Object doctorCommand(DoctorCommand command);

  @Binds
  @IntoMap
  @ClassKey(LoopCommand.class)
  Object loopCommand(LoopCommand command);
}
