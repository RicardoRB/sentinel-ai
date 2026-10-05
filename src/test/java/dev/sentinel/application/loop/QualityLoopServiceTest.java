package dev.sentinel.application.loop;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.loop.GitState;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopTerminalState;
import dev.sentinel.domain.loop.LoopRequest;
import dev.sentinel.domain.loop.LoopResult;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.application.loop.QualityLoopService;
import dev.sentinel.infrastructure.loop.GitStateInspector;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;

class QualityLoopServiceTest {
    @Test
    void orchestratesFromRequestUsingFakePortsWithoutFilesystemAdapters() {
        Path root = Path.of("memory-project");
        Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
        ProjectInspection projects = new ProjectInspection() {
            @Override public Optional<Project> detect(Path ignored) { return Optional.of(project); }
            @Override public List<Project> discover(Path ignored) { return List.of(project); }
        };
        FakeCommandExecutor commands = new FakeCommandExecutor(0, "ok", "");
        var checks = new CheckService(new dev.sentinel.application.project.ProjectDetector(projects),
                ignored -> new SentinelConfiguration(1, Map.of("tests",
                        new GateConfiguration(true, List.of("mvn", "test")))),
                new QualityGateFactory(commands), new QualityGateRunner());
        var requested = new java.util.ArrayList<String>();
        AgentRunnerFactory agents = (workingDirectory, arguments) -> {
            assertThat(workingDirectory).isEqualTo(root);
            requested.addAll(arguments);
            return new AgentRunner() {
                @Override public String id() { return "fake"; }
                @Override public AgentResult run(dev.sentinel.domain.agent.AgentRequest request) {
                    return new AgentResult(true, "done", "");
                }
            };
        };
        var loop = new QualityLoopService(checks, agents,
                ignored -> new GitState(true, "main", false, "Working tree clean."));

        var result = loop.run(new LoopRequest(root, List.of("agent", "--safe"), "fix it",
                new LoopConfiguration(1, 5, false)));

        assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
        assertThat(requested).containsExactly("agent", "--safe");
        assertThat(commands.commands).containsExactly(List.of("mvn", "test"));
    }
    @Test void passesAfterAgentAttempt(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var executor = new FakeCommandExecutor(0, "", "");
        var checks = new CheckService(new dev.sentinel.application.project.ProjectDetector(new dev.sentinel.infrastructure.project.FileSystemProjectInspection()), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        AgentRunner agent = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { return new AgentResult(true, "done", ""); } };
        var result = run(checks, agent, new GitStateInspector(executor), dir, "fix it",
                new LoopConfiguration(2, 2, true));
        assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
        assertThat(result.iterations()).isEqualTo(1);
    }

    @Test void stopsAtMaximumIterations(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var executor = new FakeCommandExecutor(1, "failure", "");
        var checks = new CheckService(new dev.sentinel.application.project.ProjectDetector(new dev.sentinel.infrastructure.project.FileSystemProjectInspection()), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        AgentRunner agent = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { return new AgentResult(true, "done", ""); } };
        var result = run(checks, agent, new GitStateInspector(executor), dir, "fix it",
                new LoopConfiguration(2, 2, true));
        assertThat(result.state()).isEqualTo(LoopTerminalState.MAX_ITERATIONS_REACHED);
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test void distinguishesAgentTimeoutAndFailure(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
        var executor = new FakeCommandExecutor(0, "", "");
        var checks = new CheckService(new dev.sentinel.application.project.ProjectDetector(new dev.sentinel.infrastructure.project.FileSystemProjectInspection()), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        AgentRunner timeout = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { try { Thread.sleep(1500); } catch (InterruptedException ignored) {} return new AgentResult(true, "late", ""); } };
        assertThat(run(checks, timeout, new GitStateInspector(executor), dir, "wait",
                new LoopConfiguration(1, 1, true)).state())
                .isEqualTo(LoopTerminalState.TIMEOUT);
        AgentRunner failure = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { return new AgentResult(false, "agent failed", ""); } };
        assertThat(run(checks, failure, new GitStateInspector(executor), dir, "fail",
                new LoopConfiguration(1, 1, true)).state())
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
        var checks = new CheckService(new dev.sentinel.application.project.ProjectDetector(new dev.sentinel.infrastructure.project.FileSystemProjectInspection()), new TomlConfigurationReader(), new QualityGateFactory(executor), new QualityGateRunner());
        var agentCalls = new int[] {0};
        AgentRunner agent = new AgentRunner() { public String id() { return "fake"; } public AgentResult run(dev.sentinel.domain.agent.AgentRequest r) { agentCalls[0]++; return new AgentResult(true, "done", ""); } };
        var result = run(checks, agent, new GitStateInspector(executor), dir, "fix it",
                new LoopConfiguration(3, 2, true));
        assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
        assertThat(agentCalls[0]).isEqualTo(2);
    }

    private static LoopResult run(CheckService checks, AgentRunner agent, GitStateInspection git,
                                  Path root, String task, LoopConfiguration configuration) {
        QualityLoopService service = new QualityLoopService(checks, (directory, arguments) -> agent, git);
        return service.run(new LoopRequest(root, java.util.List.of("fake"), task, configuration));
    }
}
