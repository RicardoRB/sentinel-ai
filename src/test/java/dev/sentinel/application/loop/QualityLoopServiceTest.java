package dev.sentinel.application.loop;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.agent.AgentRequest;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.agent.AgentRunnerFactory;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.loop.GitState;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopRequest;
import dev.sentinel.domain.loop.LoopResult;
import dev.sentinel.domain.loop.LoopTerminalState;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.loop.GitStateInspector;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityLoopServiceTest {
  @Test
  void orchestratesFromRequestUsingFakePortsWithoutFilesystemAdapters() {
    final Path root = Path.of("memory-project");
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final ProjectInspection projects =
        new ProjectInspection() {
          @Override
          public Optional<Project> detect(Path ignored) {
            return Optional.of(project);
          }

          @Override
          public List<Project> discover(Path ignored) {
            return List.of(project);
          }
        };
    final FakeCommandExecutor commands = new FakeCommandExecutor(0, "ok", "");
    final CheckService checks =
        new CheckService(
            new ProjectDetector(projects),
            ignored ->
                new SentinelConfiguration(
                    1, Map.of("tests", new GateConfiguration(true, List.of("mvn", "test")))),
            new QualityGateFactory(commands),
            new QualityGateRunner());
    final List<String> requested = new ArrayList<String>();
    final AgentRunnerFactory agents =
        (workingDirectory, arguments) -> {
          assertThat(workingDirectory).isEqualTo(root);
          requested.addAll(arguments);
          return new AgentRunner() {
            @Override
            public String id() {
              return "fake";
            }

            @Override
            public AgentResult run(AgentRequest request) {
              return new AgentResult(true, "done", "");
            }
          };
        };
    final QualityLoopService loop =
        new QualityLoopService(
            checks, agents, ignored -> new GitState(true, "main", false, "Working tree clean."));

    final LoopResult result =
        loop.run(
            new LoopRequest(
                root, List.of("agent", "--safe"), "fix it", new LoopConfiguration(1, 5, false)));

    assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
    assertThat(requested).containsExactly("agent", "--safe");
    assertThat(commands.commands).containsExactly(List.of("mvn", "test"));
  }

  @Test
  void passesAfterAgentAttempt(final @TempDir Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final CheckService checks =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner());
    final AgentRunner agent =
        new AgentRunner() {
          @Override
          public String id() {
            return "fake";
          }

          @Override
          public AgentResult run(AgentRequest r) {
            return new AgentResult(true, "done", "");
          }
        };
    final LoopResult result =
        run(
            checks,
            agent,
            new GitStateInspector(executor),
            dir,
            "fix it",
            new LoopConfiguration(2, 2, true));
    assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
    assertThat(result.iterations()).isEqualTo(1);
  }

  @Test
  void stopsAtMaximumIterations(final @TempDir Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "failure", "");
    final CheckService checks =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner());
    final AgentRunner agent =
        new AgentRunner() {
          @Override
          public String id() {
            return "fake";
          }

          @Override
          public AgentResult run(AgentRequest r) {
            return new AgentResult(true, "done", "");
          }
        };
    final LoopResult result =
        run(
            checks,
            agent,
            new GitStateInspector(executor),
            dir,
            "fix it",
            new LoopConfiguration(2, 2, true));
    assertThat(result.state()).isEqualTo(LoopTerminalState.MAX_ITERATIONS_REACHED);
    assertThat(result.iterations()).isEqualTo(2);
  }

  @Test
  void distinguishesAgentTimeoutAndFailure(final @TempDir Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final CheckService checks =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner());
    final AgentRunner timeout =
        new AgentRunner() {
          @Override
          public String id() {
            return "fake";
          }

          @Override
          public AgentResult run(AgentRequest r) {
            try {
              Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            return new AgentResult(true, "late", "");
          }
        };
    assertThat(
            run(
                    checks,
                    timeout,
                    new GitStateInspector(executor),
                    dir,
                    "wait",
                    new LoopConfiguration(1, 1, true))
                .state())
        .isEqualTo(LoopTerminalState.TIMEOUT);
    final AgentRunner failure =
        new AgentRunner() {
          @Override
          public String id() {
            return "fake";
          }

          @Override
          public AgentResult run(AgentRequest r) {
            return new AgentResult(false, "agent failed", "");
          }
        };
    assertThat(
            run(
                    checks,
                    failure,
                    new GitStateInspector(executor),
                    dir,
                    "fail",
                    new LoopConfiguration(1, 1, true))
                .state())
        .isEqualTo(LoopTerminalState.AGENT_ERROR);
  }

  @Test
  void retriesWithFeedbackUntilSuccess(final @TempDir Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
    final int[] calls = new int[] {0};
    final CommandExecutor executor =
        (command, root) -> {
          if ("git".equals(command.getFirst())) {
            return new CommandResult(1, "", "", Duration.ZERO);
          }
          calls[0]++;
          return new CommandResult(calls[0] == 2 ? 0 : 1, "", "failed", Duration.ZERO);
        };
    final CheckService checks =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner());
    final int[] agentCalls = new int[] {0};
    final AgentRunner agent =
        new AgentRunner() {
          @Override
          public String id() {
            return "fake";
          }

          @Override
          public AgentResult run(AgentRequest r) {
            agentCalls[0]++;
            return new AgentResult(true, "done", "");
          }
        };
    final LoopResult result =
        run(
            checks,
            agent,
            new GitStateInspector(executor),
            dir,
            "fix it",
            new LoopConfiguration(3, 2, true));
    assertThat(result.state()).isEqualTo(LoopTerminalState.PASSED);
    assertThat(agentCalls[0]).isEqualTo(2);
  }

  private static LoopResult run(
      final CheckService checks,
      final AgentRunner agent,
      final GitStateInspection git,
      final Path root,
      final String task,
      final LoopConfiguration configuration) {
    final QualityLoopService service =
        new QualityLoopService(checks, (directory, arguments) -> agent, git);
    return service.run(new LoopRequest(root, List.of("fake"), task, configuration));
  }
}
