package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentRequest;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.process.CommandExecutor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs an explicitly configured external coding agent without a shell. */
public final class ProcessAgentRunner implements AgentRunner {
  private final CommandExecutor executor;
  private final Path root;
  private final List<String> command;

  public ProcessAgentRunner(CommandExecutor executor, Path root, List<String> command) {
    this.executor = executor;
    this.root = root;
    this.command = List.copyOf(command);
  }

  @Override
  public String id() {
    return "external";
  }

  @Override
  public AgentResult run(AgentRequest request) {
    final List<String> args = new ArrayList<>(command);
    args.add(request.task());
    final var result = executor.execute(args, root);
    return new AgentResult(
        result.succeeded(),
        result.succeeded() ? "Agent completed." : "Agent failed.",
        result.stdout() + result.stderr());
  }
}
