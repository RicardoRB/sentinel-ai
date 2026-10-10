package dev.sentinel.domain.process;

import java.nio.file.Path;
import java.util.List;

/**
 * Port for running external processes.
 *
 * <p>The command is an argument vector, never a shell string: implementations must not interpret it
 * through a shell. Implementations must not throw for a command that fails or cannot be started;
 * they report that through the {@link CommandResult}.
 */
@FunctionalInterface
public interface CommandExecutor {

  default CommandResult execute(final Command command) {
    return execute(command.arguments(), command.workingDirectory());
  }

  CommandResult execute(List<String> command, Path workingDirectory);
}
