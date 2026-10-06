package dev.sentinel;

import com.google.inject.Guice;
import dev.sentinel.config.SentinelModule;
import dev.sentinel.infrastructure.cli.CommandLineRunnerImpl;

public final class SentinelApplication {
  private SentinelApplication() {}

  public static void main(String[] args) {
    CommandLineRunnerImpl runner =
        Guice.createInjector(new SentinelModule()).getInstance(CommandLineRunnerImpl.class);
    System.exit(runner.run(args));
  }
}
