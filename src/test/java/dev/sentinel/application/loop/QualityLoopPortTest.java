package dev.sentinel.application.loop;

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
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.loop.LoopRequest;
import dev.sentinel.domain.loop.LoopResult;
import dev.sentinel.domain.loop.LoopTerminalState;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QualityLoopPortTest {
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
    final List<String> requested = new ArrayList<>();
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
}
