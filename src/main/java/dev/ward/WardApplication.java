package dev.ward;

import dev.ward.config.DaggerWardComponent;
import dev.ward.infrastructure.cli.CommandLineRunnerImpl;

/** The {@code ward} executable entry point. */
public final class WardApplication {
  private WardApplication() {}

  /** Runs the command line {@code args} and exits with the command's exit code. */
  public static void main(final String[] args) {
    final CommandLineRunnerImpl runner = DaggerWardComponent.create().commandLineRunner();
    System.exit(runner.run(args));
  }
}
