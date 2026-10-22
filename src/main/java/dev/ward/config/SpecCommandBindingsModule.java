package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import dev.ward.infrastructure.cli.spec.SpecCommand;
import dev.ward.infrastructure.cli.spec.SpecEnforceCommand;
import dev.ward.infrastructure.cli.spec.SpecGuardCommand;
import dev.ward.infrastructure.cli.spec.SpecValidateCommand;

/** Command bindings for the {@code ward spec} command group. */
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
