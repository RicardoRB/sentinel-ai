package dev.sentinel.config;

import dagger.Module;

@Module(
    includes = {
      ProcessBindingsModule.class,
      ProjectBindingsModule.class,
      InitBindingsModule.class,
      TraceabilityBindingsModule.class
    })
interface PortBindingsModule {}
