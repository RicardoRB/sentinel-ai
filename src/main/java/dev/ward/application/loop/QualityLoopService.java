package dev.ward.application.loop;

import dev.ward.application.gate.CheckService;
import dev.ward.domain.agent.AgentRequest;
import dev.ward.domain.agent.AgentResult;
import dev.ward.domain.agent.AgentRunner;
import dev.ward.domain.agent.AgentRunnerFactory;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.loop.GitState;
import dev.ward.domain.loop.GitStateInspection;
import dev.ward.domain.loop.LoopConfiguration;
import dev.ward.domain.loop.LoopRequest;
import dev.ward.domain.loop.LoopResult;
import dev.ward.domain.loop.LoopTerminalState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Runs an agent and the quality gates in a loop until the gates pass, the agent fails, or the
 * iterations run out.
 */
public final class QualityLoopService {
  private static final Logger LOGGER = Logger.getLogger(QualityLoopService.class.getName());
  private final CheckService checks;
  private final AgentRunnerFactory agents;
  private final GitStateInspection git;

  /**
   * Creates the service. {@code agents} starts the agent, and {@code git} checks the working tree
   * before the loop starts.
   */
  @Inject
  public QualityLoopService(
      final CheckService checks, final AgentRunnerFactory agents, final GitStateInspection git) {
    this.checks = checks;
    this.agents = agents;
    this.git = git;
  }

  /** Runs the loop for {@code request}, feeding each failed check back to the agent. */
  public LoopResult run(final LoopRequest request) {
    final Path root = request.workingDirectory();
    final String task = request.task();
    final LoopConfiguration config = request.configuration();
    final AgentRunner agent = agents.create(root, request.agentCommand());
    final GitState state = git.inspect(root);
    if (state.dirty() && !config.allowDirty()) {
      LOGGER.log(Level.INFO, "event=loop-terminal status=dirty-working-tree");
      return new LoopResult(
          LoopTerminalState.GATE_ERROR,
          0,
          List.of(),
          "Refusing to start with a dirty working tree.");
    }
    final LoopContext context = new LoopContext(agent, root, config.timeoutSeconds());
    final List<CheckReport> history = new ArrayList<>();
    String feedback = task;
    for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
      final int currentIteration = iteration;
      LOGGER.log(Level.INFO, () -> "event=loop-iteration-start iteration=" + currentIteration);
      final Optional<LoopResult> outcome = iterate(context, feedback, iteration, history);
      if (outcome.isPresent()) {
        LOGGER.log(
            Level.INFO,
            () ->
                "event=loop-terminal iteration="
                    + currentIteration
                    + " status="
                    + outcome.get().state());
        return outcome.get();
      }
      feedback = feedback(history.getLast());
    }
    LOGGER.log(Level.INFO, "event=loop-terminal status=max-iterations");
    return new LoopResult(
        LoopTerminalState.MAX_ITERATIONS_REACHED,
        config.maxIterations(),
        history,
        "Maximum iterations reached.");
  }

  /** Runs one agent attempt and quality check; returns a result when the loop must stop. */
  private Optional<LoopResult> iterate(
      final LoopContext context,
      final String task,
      final int iteration,
      final List<CheckReport> history) {
    final AgentResult agentResult;
    try {
      agentResult = runAgent(context.agent(), task, context.timeoutSeconds());
    } catch (TimeoutException e) {
      LOGGER.log(Level.WARNING, "event=loop-terminal status=timeout");
      return Optional.of(
          new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Agent timeout."));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.log(Level.WARNING, "event=loop-terminal status=interrupted");
      return Optional.of(
          new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Loop interrupted."));
    } catch (ExecutionException e) {
      LOGGER.log(Level.WARNING, "event=loop-terminal status=agent-error");
      return Optional.of(
          new LoopResult(
              LoopTerminalState.AGENT_ERROR, iteration, history, e.getCause().toString()));
    }
    if (!agentResult.succeeded()) {
      LOGGER.log(Level.INFO, "event=loop-terminal status=agent-error");
      return Optional.of(
          new LoopResult(LoopTerminalState.AGENT_ERROR, iteration, history, agentResult.summary()));
    }
    return checkQuality(context.root(), iteration, history);
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
  private Optional<LoopResult> checkQuality(
      final Path root, final int iteration, final List<CheckReport> history) {
    final CheckReport report;
    final FutureTask<CheckReport> check = new FutureTask<>(() -> checks.check(root));
    check.run();
    try {
      report = check.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      LOGGER.log(Level.WARNING, "event=loop-terminal status=gate-interrupted");
      return Optional.of(
          new LoopResult(LoopTerminalState.GATE_ERROR, iteration, history, "Loop interrupted."));
    } catch (ExecutionException failure) {
      LOGGER.log(Level.WARNING, "event=loop-terminal status=gate-error");
      final Throwable cause = failure.getCause();
      return Optional.of(
          new LoopResult(
              LoopTerminalState.GATE_ERROR,
              iteration,
              history,
              cause == null ? failure.getMessage() : cause.getMessage()));
    }
    history.add(report);
    if (report.passed()) {
      LOGGER.log(Level.INFO, "event=loop-terminal status=passed");
      return Optional.of(
          new LoopResult(
              LoopTerminalState.PASSED, iteration, history, "All quality gates passed."));
    }
    return Optional.empty();
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

  /** The agent, project root, and per-attempt timeout that stay fixed for one loop. */
  private record LoopContext(AgentRunner agent, Path root, int timeoutSeconds) {}
}
