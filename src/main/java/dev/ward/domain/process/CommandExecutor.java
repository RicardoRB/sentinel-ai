package dev.ward.domain.process;

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

  /** Runs {@code command} in its working directory. */
  default CommandResult execute(final Command command) {
    return execute(command.arguments(), command.workingDirectory());
  }

  /** Runs the argument vector {@code command} in {@code workingDirectory} and waits for it. */
  CommandResult execute(List<String> command, Path workingDirectory);
}
