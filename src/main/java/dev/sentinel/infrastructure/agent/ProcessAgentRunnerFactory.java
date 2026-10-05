package dev.sentinel.infrastructure.agent;

import com.google.inject.Inject;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.process.CommandExecutor;

import java.nio.file.Path;
import java.util.List;

/** Creates shell-free process-backed agent runners from request-specific arguments. */
public final class ProcessAgentRunnerFactory implements AgentRunnerFactory {
    private final CommandExecutor executor;

    @Inject
    public ProcessAgentRunnerFactory(CommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public AgentRunner create(Path workingDirectory, List<String> arguments) {
        return new ProcessAgentRunner(executor, workingDirectory, arguments);
    }
}
