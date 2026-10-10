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
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

public final class QualityLoopService {
  private final CheckService checks;
  private final AgentRunnerFactory agents;
  private final GitStateInspection git;

  @Inject
  public QualityLoopService(
      final CheckService checks, final AgentRunnerFactory agents, final GitStateInspection git) {
    this.checks = checks;
    this.agents = agents;
    this.git = git;
  }

  public LoopResult run(final LoopRequest request) {
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
      final Optional<LoopResult> outcome =
          iterate(agent, feedback, root, config.timeoutSeconds(), iteration, history);
      if (outcome.isPresent()) {
        return outcome.get();
      }
      feedback = feedback(history.getLast());
    }
    return new LoopResult(
        LoopTerminalState.MAX_ITERATIONS_REACHED,
        config.maxIterations(),
        history,
        "Maximum iterations reached.");
  }

  /** Runs one agent attempt and quality check; returns a result when the loop must stop. */
  private Optional<LoopResult> iterate(
      final AgentRunner agent,
      final String task,
      final Path root,
      final int timeoutSeconds,
      final int iteration,
      final List<CheckReport> history) {
    final AgentResult agentResult;
    try {
      agentResult = runAgent(agent, task, timeoutSeconds);
    } catch (TimeoutException e) {
      return Optional.of(
          new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Agent timeout."));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.of(
          new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Loop interrupted."));
    } catch (ExecutionException e) {
      return Optional.of(
          new LoopResult(
              LoopTerminalState.AGENT_ERROR, iteration, history, e.getCause().toString()));
    }
    if (!agentResult.succeeded()) {
      return Optional.of(
          new LoopResult(LoopTerminalState.AGENT_ERROR, iteration, history, agentResult.summary()));
    }
    return checkQuality(root, iteration, history);
  }

  private static AgentResult runAgent(
      final AgentRunner agent, final String task, final int timeoutSeconds)
      throws TimeoutException, InterruptedException, ExecutionException {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      final Future<AgentResult> future =
          executor.submit(() -> agent.run(new AgentRequest(task, timeoutSeconds)));
      try {
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        future.cancel(true);
        throw e;
      }
    }
  }

  // Any gate failure must end the loop as GATE_ERROR rather than escape it.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private Optional<LoopResult> checkQuality(
      final Path root, final int iteration, final List<CheckReport> history) {
    final CheckReport report;
    try {
      report = checks.check(root);
    } catch (RuntimeException e) {
      return Optional.of(
          new LoopResult(LoopTerminalState.GATE_ERROR, iteration, history, e.getMessage()));
    }
    history.add(report);
    return report.passed()
        ? Optional.of(
            new LoopResult(
                LoopTerminalState.PASSED, iteration, history, "All quality gates passed."))
        : Optional.empty();
  }

  private static String feedback(final CheckReport report) {
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
