package dev.sentinel;

import dev.sentinel.config.DaggerSentinelComponent;
import dev.sentinel.infrastructure.cli.CommandLineRunnerImpl;

public final class SentinelApplication {
  private SentinelApplication() {}

  public static void main(String[] args) {
    CommandLineRunnerImpl runner = DaggerSentinelComponent.create().commandLineRunner();
    System.exit(runner.run(args));
  }
}
