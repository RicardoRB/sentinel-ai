package dev.ward.infrastructure.cli.agent;

import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;

/** The {@code ward hook} command group; it runs only through a subcommand. */
@Command(
    name = "hook",
    description = "Run an agent hook adapter.",
    subcommands = {ClaudeCodeHookCommand.class, NativeHookCommand.class})
public final class HookCommand implements Callable<Integer> {
  /** Creates the command group. */
  @Inject
  public HookCommand() {}

  @Override
  public Integer call() {
    return 2;
  }
}
