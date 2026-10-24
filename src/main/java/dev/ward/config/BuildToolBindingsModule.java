package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.BuildToolConfigurator;
import dev.ward.infrastructure.init.JvmBuildToolConfigurator;
import java.util.Map;

/** Build-file adapters, one per supported toolchain, behind the routing configurator. */
@Module
final class BuildToolBindingsModule {
  private BuildToolBindingsModule() {}

  @Provides
  static BuildToolConfiguration buildToolConfiguration(final BuildToolConfigurator router) {
    return router;
  }

  @Provides
  static Map<Toolchain, BuildToolConfiguration> buildToolAdapters(
      final JvmBuildToolConfigurator jvm) {
    return Map.of(Toolchain.JVM, jvm);
  }
}
