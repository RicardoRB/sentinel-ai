package dev.sentinel.domain;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class FakeCommandExecutor implements CommandExecutor {

  public final List<List<String>> commands = new ArrayList<>();
  public final List<Path> workingDirectories = new ArrayList<>();
  private final CommandResult result;

  public FakeCommandExecutor(final int exitCode, final String stdout, final String stderr) {
    this.result = new CommandResult(exitCode, stdout, stderr, Duration.ofMillis(1234));
  }

  @Override
  public CommandResult execute(final List<String> command, final Path workingDirectory) {
    commands.add(command);
    workingDirectories.add(workingDirectory);
    return result;
  }
}
