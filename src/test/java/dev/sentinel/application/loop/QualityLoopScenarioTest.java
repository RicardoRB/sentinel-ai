package dev.sentinel.application.loop;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.agent.AgentRequest;
import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.agent.AgentRunner;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.loop.GitState;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopRequest;
import dev.sentinel.domain.loop.LoopResult;
import dev.sentinel.domain.loop.LoopTerminalState;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.loop.GitStateInspector;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityLoopScenarioTest {
  private static final String GIT_COMMAND = "git";

  @Test
  void passesAfterAgentAttempt(@TempDir final Path dir) throws Exception {
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
  void stopsAtMaximumIterations(@TempDir final Path dir) throws Exception {
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
  void distinguishesAgentTimeoutAndFailure(@TempDir final Path dir) throws Exception {
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
  void retriesWithFeedbackUntilSuccess(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"verify\"\n");
    final int[] calls = {0};
    final CommandExecutor executor =
        (command, root) -> {
          if (GIT_COMMAND.equals(command.getFirst())) {
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
    final int[] agentCalls = {0};
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

  @Test
  void convertsUnexpectedGateFailureToGateError(@TempDir final Path dir) {
    final CheckService checks =
        new CheckService(null, null, null, null) {
          @Override
          public CheckReport check(final Path ignored) {
            throw new IllegalStateException("gate exploded");
          }
        };
    final AgentRunner agent =
        new AgentRunner() {
          @Override
          public String id() {
            return "fake";
          }

          @Override
          public AgentResult run(final AgentRequest request) {
            return new AgentResult(true, "done", "");
          }
        };

    final LoopResult result =
        run(
            checks,
            agent,
            ignored -> new GitState(false, "main", false, "clean"),
            dir,
            "fix it",
            new LoopConfiguration(1, 2, true));

    assertThat(result.state()).isEqualTo(LoopTerminalState.GATE_ERROR);
    assertThat(result.message()).isEqualTo("gate exploded");
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
