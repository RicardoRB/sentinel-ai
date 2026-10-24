package dev.ward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.ProjectFixtures;
import dev.ward.application.gate.CheckService;
import dev.ward.application.gate.QualityGateFactory;
import dev.ward.application.gate.QualityGateRunner;
import dev.ward.application.project.ProjectDetector;
import dev.ward.config.DaggerWardComponent;
import dev.ward.domain.agent.AgentRequest;
import dev.ward.domain.config.WardException;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.agent.ProcessAgentRunner;
import dev.ward.infrastructure.cli.CommandLineRunnerImpl;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// This test intentionally aggregates coverage across the application services.
// Splitting it would duplicate setup and obscure the cross-service scenarios.
class CoverageExecutionTest {
  @Test
  void processAgentRunnerAddsTaskAndReportsSuccessOrFailure() {
    final CommandExecutor executor =
        (command, root) ->
            new CommandResult(command.contains("fail") ? 1 : 0, "out", "err", Duration.ZERO);
    final ProcessAgentRunner runner =
        new ProcessAgentRunner(executor, Path.of("/tmp"), List.of("agent"));
    assertThat(runner.id()).isEqualTo("external");
    assertThat(runner.run(new AgentRequest("fix", 2)).succeeded()).isTrue();
    final ProcessAgentRunner failing =
        new ProcessAgentRunner(executor, Path.of("/tmp"), List.of("fail"));
    assertThat(failing.run(new AgentRequest("fix", 2)).summary()).isEqualTo("Agent failed.");
  }

  @Test
  void checkServiceSelectsProfilesAndRejectsInvalidOnes(@TempDir final Path root)
      throws IOException {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    Files.writeString(
        root.resolve("ward.toml"),
        """
                version = 1
                 [quality-gates.tests]
                 command = "test"
                 profiles = ["tests-only"]
                 [quality-gates.compile]
                 command = "compile"
                """);
    final CommandExecutor executor =
        (command, path) -> new CommandResult(0, "ok", "", Duration.ZERO);
    final CheckService service =
        new CheckService(
            new ProjectDetector(new FileSystemProjectInspection()),
            new TomlConfigurationReader(),
            new QualityGateFactory(executor),
            new QualityGateRunner(),
            new FilePathExistence());
    assertThat(service.check(root, List.of("tests-only")).results()).hasSize(1);
    assertThatThrownBy(() -> service.check(root, List.of("missing")))
        .isInstanceOf(WardException.class);
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 1\n[quality-gates.tests]\ncommand='test'\nprofiles=['bad']\n");
    assertThatThrownBy(() -> service.check(root))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("--profile");
  }

  @Test
  void commandLineRunnerWiresCommandsAndHandlesKnownAndUnexpectedFailures(@TempDir final Path root)
      throws IOException {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final CommandLineRunnerImpl runner = DaggerWardComponent.create().commandLineRunner();
    assertThat(runner.run()).isEqualTo(ExitCodes.ERROR);
    assertThat(runner.run("--help")).isZero();
    assertThat(runner.run("detect", "-C", root.toString())).isZero();
    Files.writeString(
        root.resolve("ward.toml"),
        "version = 1\n\n[quality-gates.tests]\nenabled = true\ncommand = \"echo ok\"\n");
    assertThat(runner.run("check", "-C", root.toString())).isZero();
    assertThat(runner.run("unknown-command")).isEqualTo(2);
  }
}
