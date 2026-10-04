package dev.sentinel.application;

import dev.sentinel.domain.agent.AgentRequest;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopResult;
import dev.sentinel.domain.loop.LoopTerminalState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class QualityLoopService {
    private final CheckService checks;
    private final AgentRunner agent;
    private final GitStateInspector git;

    public QualityLoopService(CheckService checks, AgentRunner agent, GitStateInspector git) {
        this.checks = checks;
        this.agent = agent;
        this.git = git;
    }

    public LoopResult run(Path root, String task, LoopConfiguration config) {
        GitState state = git.inspect(root);
        if (state.dirty() && !config.allowDirty()) {
            return new LoopResult(LoopTerminalState.GATE_ERROR, 0, List.of(), "Refusing to start with a dirty working tree.");
        }
        List<CheckReport> history = new ArrayList<>();
        String feedback = task;
        for (int iteration = 1; iteration <= config.maxIterations(); iteration++) {
            AgentResult agentResult;
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                String request = feedback;
                Future<AgentResult> future = executor.submit(() -> agent.run(new AgentRequest(request, config.timeoutSeconds())));
                try {
                    agentResult = future.get(config.timeoutSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    return new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Agent timeout.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new LoopResult(LoopTerminalState.TIMEOUT, iteration, history, "Loop interrupted.");
                } catch (ExecutionException e) {
                    return new LoopResult(LoopTerminalState.AGENT_ERROR, iteration, history, e.getCause().toString());
                }
            }
            if (!agentResult.succeeded()) return new LoopResult(LoopTerminalState.AGENT_ERROR, iteration, history, agentResult.summary());
            CheckReport report;
            try { report = checks.check(root); }
            catch (RuntimeException e) { return new LoopResult(LoopTerminalState.GATE_ERROR, iteration, history, e.getMessage()); }
            history.add(report);
            if (report.passed()) return new LoopResult(LoopTerminalState.PASSED, iteration, history, "All quality gates passed.");
            feedback = feedback(report);
        }
        return new LoopResult(LoopTerminalState.MAX_ITERATIONS_REACHED, config.maxIterations(), history,
                "Maximum iterations reached.");
    }

    private static String feedback(CheckReport report) {
        return report.results().stream().filter(result -> !result.passed())
                .map(result -> "gate=" + result.name() + " status=" + result.status() + " output=" + result.stdout() + result.stderr())
                .reduce("Fix these failed quality gates:\n", (a, b) -> a + b + "\n");
    }
}
