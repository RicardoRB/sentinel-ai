package dev.sentinel;

import dev.sentinel.config.DaggerSentinelComponent;
import dev.sentinel.infrastructure.cli.CommandLineRunnerImpl;

/** The {@code sentinel} executable entry point. */
public final class SentinelApplication {
  private SentinelApplication() {}

  /** Runs the command line {@code args} and exits with the command's exit code. */
  public static void main(final String[] args) {
    final CommandLineRunnerImpl runner = DaggerSentinelComponent.create().commandLineRunner();
    System.exit(runner.run(args));
  }
}
