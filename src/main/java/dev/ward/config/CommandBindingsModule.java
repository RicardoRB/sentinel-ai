package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.ward.infrastructure.cli.WardCommand;
import dev.ward.infrastructure.cli.agent.ClaudeCodeHookCommand;
import dev.ward.infrastructure.cli.agent.HookCommand;
import dev.ward.infrastructure.cli.agent.IntegrateCommand;
import dev.ward.infrastructure.cli.agent.NativeHookCommand;
import dev.ward.infrastructure.cli.doctor.DoctorCommand;
import dev.ward.infrastructure.cli.gate.CheckCommand;
import dev.ward.infrastructure.cli.init.InitCommand;
import dev.ward.infrastructure.cli.loop.LoopCommand;
import dev.ward.infrastructure.cli.project.DetectCommand;

@Module
interface CommandBindingsModule {
  @Binds
  @IntoMap
  @ClassKey(WardCommand.class)
  Object wardCommand(WardCommand command);

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
  @ClassKey(HookCommand.class)
  Object hookCommand(HookCommand command);

  @Binds
  @IntoMap
  @ClassKey(ClaudeCodeHookCommand.class)
  Object claudeCodeHookCommand(ClaudeCodeHookCommand command);

  @Binds
  @IntoMap
  @ClassKey(NativeHookCommand.class)
  Object nativeHookCommand(NativeHookCommand command);

  @Binds
  @IntoMap
  @ClassKey(DoctorCommand.class)
  Object doctorCommand(DoctorCommand command);

  @Binds
  @IntoMap
  @ClassKey(LoopCommand.class)
  Object loopCommand(LoopCommand command);
}
