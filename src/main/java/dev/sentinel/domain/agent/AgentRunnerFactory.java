package dev.sentinel.domain.agent;

import java.nio.file.Path;
import java.util.List;

/** Creates a runner for runtime-selected agent command arguments and working directory. */
@FunctionalInterface
public interface AgentRunnerFactory {
  /**
   * Creates a runner that starts the agent command {@code arguments} in {@code workingDirectory}.
   */
  AgentRunner create(Path workingDirectory, List<String> arguments);
}
