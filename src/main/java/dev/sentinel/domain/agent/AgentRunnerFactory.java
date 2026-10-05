package dev.sentinel.domain.agent;

import java.nio.file.Path;
import java.util.List;

/** Creates a runner for runtime-selected agent command arguments and working directory. */
public interface AgentRunnerFactory {
  AgentRunner create(Path workingDirectory, List<String> arguments);
}
