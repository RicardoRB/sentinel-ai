package dev.ward.config;

import dagger.Component;
import dev.ward.infrastructure.cli.CommandLineRunnerImpl;
import java.util.Map;
import javax.inject.Provider;

/** The compile-time Dagger graph that builds the command-line runner and every command. */
@Component(
    modules = {
      PortBindingsModule.class,
      CommandBindingsModule.class,
      SpecCommandBindingsModule.class,
      WardProvidesModule.class
    })
public interface WardComponent {
  /** Returns the runner that parses and executes the command line. */
  CommandLineRunnerImpl commandLineRunner();

  /** Returns a provider for each command class, used by the Picocli command factory. */
  Map<Class<?>, Provider<Object>> commands();
}
