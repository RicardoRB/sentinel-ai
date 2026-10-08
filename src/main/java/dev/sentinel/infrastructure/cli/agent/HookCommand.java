package dev.sentinel.infrastructure.cli.agent;

import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;

@Command(
    name = "hook",
    description = "Run an agent hook adapter.",
    subcommands = {ClaudeCodeHookCommand.class})
public final class HookCommand implements Callable<Integer> {
  @Inject
  public HookCommand() {}

  @Override
  public Integer call() {
    return 2;
  }
}
