package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentRequest;
import dev.ward.domain.agent.AgentResult;
import dev.ward.domain.agent.AgentRunner;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs an explicitly configured external coding agent without a shell. */
public final class ProcessAgentRunner implements AgentRunner {
  private final CommandExecutor executor;
  private final Path root;
  private final List<String> command;

  /**
   * Creates a runner that starts {@code command} in {@code root} with the task as its last
   * argument.
   */
  public ProcessAgentRunner(
      final CommandExecutor executor, final Path root, final List<String> command) {
    this.executor = executor;
    this.root = root;
    this.command = List.copyOf(command);
  }

  @Override
  public String id() {
    return "external";
  }

  @Override
  public AgentResult run(final AgentRequest request) {
    final List<String> args = new ArrayList<>(command);
    args.add(request.task());
    final CommandResult result = executor.execute(args, root);
    return new AgentResult(
        result.succeeded(),
        result.succeeded() ? "Agent completed." : "Agent failed.",
        result.stdout() + result.stderr());
  }
}
