package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.sentinel.infrastructure.cli.spec.SpecCommand;
import dev.sentinel.infrastructure.cli.spec.SpecEnforceCommand;
import dev.sentinel.infrastructure.cli.spec.SpecGuardCommand;
import dev.sentinel.infrastructure.cli.spec.SpecValidateCommand;

/** Command bindings for the {@code sentinel spec} command group. */
@Module
interface SpecCommandBindingsModule {
  @Binds
  @IntoMap
  @ClassKey(SpecCommand.class)
  Object specCommand(SpecCommand command);

  @Binds
  @IntoMap
  @ClassKey(SpecValidateCommand.class)
  Object specValidateCommand(SpecValidateCommand command);

  @Binds
  @IntoMap
  @ClassKey(SpecGuardCommand.class)
  Object specGuardCommand(SpecGuardCommand command);

  @Binds
  @IntoMap
  @ClassKey(SpecEnforceCommand.class)
  Object specEnforceCommand(SpecEnforceCommand command);
}
