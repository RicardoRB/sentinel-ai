package dev.sentinel.infrastructure.cli;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import picocli.CommandLine;

/** Lets Picocli obtain commands from the Dagger component. */
public class DaggerCommandFactory implements CommandLine.IFactory {
  private final Map<Class<?>, Provider<Object>> commands;

  @Inject
  public DaggerCommandFactory(Map<Class<?>, Provider<Object>> commands) {
    this.commands = Map.copyOf(commands);
  }

  @Override
  public <K> K create(Class<K> type) throws Exception {
    Provider<Object> provider = commands.get(type);
    return provider == null ? CommandLine.defaultFactory().create(type) : type.cast(provider.get());
  }
}
