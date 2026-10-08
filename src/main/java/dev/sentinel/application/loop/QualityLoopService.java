package dev.sentinel.application.loop;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.domain.agent.AgentRequest;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.loop.GitState;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopRequest;
import dev.sentinel.domain.loop.LoopResult;
import dev.sentinel.domain.loop.LoopTerminalState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import javax.inject.Inject;

public final class QualityLoopService {
  private final CheckService checks;
  private final AgentRunnerFactory agents;
  private final GitStateInspection git;

  @Inject
  public QualityLoopService(
      CheckService checks, AgentRunnerFactory agents, GitStateInspection git) {
    this.checks = checks;
    this.agents = agents;
    this.git = git;
  }

  public LoopResult run(LoopRequest request) {
    final Path root = request.workingDirectory();
    final String task = request.task();
    final LoopConfiguration config = request.configuration();
    final AgentRunner agent = agents.create(root, request.agentCommand());
    final GitState state = git.inspect(root);
    if (state.dirty() && !config.allowDirty()) {
      return new LoopResult(
          LoopTerminalState.GATE_ERROR,
          0,
          List.of(),
          "Refusing to start with a dirty working tree.");
    }
    final List<CheckReport> history = new ArrayList<>();
    String feedback = task;
    for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
      final AgentResult agentResult;
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        final String agentTask = feedback;
        final Future<AgentResult> future =
            executor.submit(() -> agent.run(new AgentRequest(agentTask, config.timeoutSeconds())));
        try {
          agentResult = future.get(config.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
          future.cancel(true);
          return new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Agent timeout.");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Loop interrupted.");
        } catch (ExecutionException e) {
          return new LoopResult(
              LoopTerminalState.AGENT_ERROR, iteration, history, e.getCause().toString());
        }
      }
      if (!agentResult.succeeded()) {
        return new LoopResult(
            LoopTerminalState.AGENT_ERROR, iteration, history, agentResult.summary());
      }
      final CheckReport report;
      try {
        report = checks.check(root);
      } catch (RuntimeException e) {
        return new LoopResult(LoopTerminalState.GATE_ERROR, iteration, history, e.getMessage());
      }
      history.add(report);
      if (report.passed()) {
        return new LoopResult(
            LoopTerminalState.PASSED, iteration, history, "All quality gates passed.");
      }
      feedback = feedback(report);
    }
    return new LoopResult(
        LoopTerminalState.MAX_ITERATIONS_REACHED,
        config.maxIterations(),
        history,
        "Maximum iterations reached.");
  }

  private static String feedback(CheckReport report) {
    return report.results().stream()
        .filter(result -> !result.passed())
        .map(
            result ->
                "gate="
                    + result.name()
                    + " status="
                    + result.status()
                    + " output="
                    + result.stdout()
                    + result.stderr())
        .reduce("Fix these failed quality gates:\n", (a, b) -> a + b + "\n");
  }
}
