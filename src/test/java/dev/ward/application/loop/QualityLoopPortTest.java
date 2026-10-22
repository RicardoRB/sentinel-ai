package dev.ward.application.loop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.gate.CheckService;
import dev.ward.application.gate.QualityGateFactory;
import dev.ward.application.gate.QualityGateRunner;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.AgentRequest;
import dev.ward.domain.agent.AgentResult;
import dev.ward.domain.agent.AgentRunner;
import dev.ward.domain.agent.AgentRunnerFactory;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.loop.GitState;
import dev.ward.domain.loop.LoopConfiguration;
import dev.ward.domain.loop.LoopRequest;
import dev.ward.domain.loop.LoopResult;
import dev.ward.domain.loop.LoopTerminalState;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import dev.ward.infrastructure.config.FilePathExistence;
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
                new WardConfiguration(
                    1, Map.of("tests", new GateConfiguration(true, List.of("mvn", "test")))),
            new QualityGateFactory(commands),
            new QualityGateRunner(),
            new FilePathExistence());
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
