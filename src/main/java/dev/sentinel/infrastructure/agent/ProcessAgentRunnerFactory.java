package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.process.CommandExecutor;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

/** Creates shell-free process-backed agent runners from request-specific arguments. */
public final class ProcessAgentRunnerFactory implements AgentRunnerFactory {
  private final CommandExecutor executor;

  /** Creates the factory; {@code executor} starts the agent processes. */
  @Inject
  public ProcessAgentRunnerFactory(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public AgentRunner create(final Path workingDirectory, final List<String> arguments) {
    return new ProcessAgentRunner(executor, workingDirectory, arguments);
  }
}
