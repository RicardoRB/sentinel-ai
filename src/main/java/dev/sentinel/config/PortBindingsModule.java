package dev.sentinel.config;

import dagger.Module;

@Module(
    includes = {ProcessBindingsModule.class, ProjectBindingsModule.class, InitBindingsModule.class})
interface PortBindingsModule {}
