package dev.sentinel.application;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopTerminalState;
import dev.sentinel.infrastructure.TomlConfigurationReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;

class QualityLoopServiceTest {
    @Test void passesAfterAgentAttempt(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var executor = new FakeCommandExecutor(0, "", "");
        var checks = new CheckService(new ProjectDetector(), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        AgentRunner agent = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { return new AgentResult(true, "done", ""); } };
        var result = new QualityLoopService(checks, agent, new GitStateInspector(executor)).run(dir, "fix it", new LoopConfiguration(2, 2, true));
        assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
        assertThat(result.iterations()).isEqualTo(1);
    }

    @Test void stopsAtMaximumIterations(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var executor = new FakeCommandExecutor(1, "failure", "");
        var checks = new CheckService(new ProjectDetector(), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        AgentRunner agent = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { return new AgentResult(true, "done", ""); } };
        var result = new QualityLoopService(checks, agent, new GitStateInspector(executor)).run(dir, "fix it", new LoopConfiguration(2, 2, true));
        assertThat(result.state()).isEqualTo(LoopTerminalState.MAX_ITERATIONS_REACHED);
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test void distinguishesAgentTimeoutAndFailure(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var executor = new FakeCommandExecutor(0, "", "");
        var checks = new CheckService(new ProjectDetector(), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        AgentRunner timeout = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { try { Thread.sleep(1500); } catch (InterruptedException ignored) {} return new AgentResult(true, "late", ""); } };
        assertThat(new QualityLoopService(checks, timeout, new GitStateInspector(executor)).run(dir, "wait", new LoopConfiguration(1, 1, true)).state())
                .isEqualTo(LoopTerminalState.TIMEOUT);
        AgentRunner failure = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { return new AgentResult(false, "agent failed", ""); } };
        assertThat(new QualityLoopService(checks, failure, new GitStateInspector(executor)).run(dir, "fail", new LoopConfiguration(1, 1, true)).state())
                .isEqualTo(LoopTerminalState.AGENT_ERROR);
    }

    @Test void retriesWithFeedbackUntilSuccess(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var calls = new int[] {0};
        dev.sentinel.domain.process.CommandExecutor executor = (command, root) -> {
            if (command.getFirst().equals("git")) return new dev.sentinel.domain.process.CommandResult(1, "", "", java.time.Duration.ZERO);
            return new dev.sentinel.domain.process.CommandResult(++calls[0] == 2 ? 0 : 1, "", "failed", java.time.Duration.ZERO);
        };
        var checks = new CheckService(new ProjectDetector(), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        var agentCalls = new int[] {0};
        AgentRunner agent = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { agentCalls[0]++; return new AgentResult(true, "done", ""); } };
        var result = new QualityLoopService(checks, agent, new GitStateInspector(executor)).run(dir, "fix it", new LoopConfiguration(3, 2, true));
        assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
        assertThat(agentCalls[0]).isEqualTo(2);
    }
}
