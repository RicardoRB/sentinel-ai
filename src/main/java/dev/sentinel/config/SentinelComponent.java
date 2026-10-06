package dev.sentinel.config;

import dagger.Component;
import dev.sentinel.infrastructure.cli.CommandLineRunnerImpl;
import java.util.Map;
import javax.inject.Provider;

@Component(
    modules = {PortBindingsModule.class, CommandBindingsModule.class, SentinelProvidesModule.class})
public interface SentinelComponent {
  CommandLineRunnerImpl commandLineRunner();

  Map<Class<?>, Provider<Object>> commands();
}
