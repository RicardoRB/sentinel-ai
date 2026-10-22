package dev.ward.application.gate;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckServiceTest {

  @Test
  void orchestratesWithInMemoryProjectAndConfigurationPorts() {
    final Path root = Path.of("in-memory-project");
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
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "ok", "");
    final CheckService checks =
        new CheckService(
            new ProjectDetector(projects),
            file ->
                new WardConfiguration(
                    1, Map.of("tests", new GateConfiguration(true, List.of("mvn", "test")))),
            new QualityGateFactory(executor),
            new QualityGateRunner(),
            Files::exists);

    final CheckReport report = checks.check(root);

    assertThat(report.passed()).isTrue();
    assertThat(executor.commands).containsExactly(List.of("mvn", "test"));
    assertThat(executor.workingDirectories).containsExactly(root);
  }

  private CheckService service(final FakeCommandExecutor executor) {
    return CheckServiceFixtures.checks(executor);
  }

  @Test
  void runsConfiguredCommandNotAHardcodedOne(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"),
        """
                version = 1
                [quality-gates.tests]
                command = "make verify"
                """);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final CheckReport report = service(executor).check(dir);

    assertThat(report.passed()).isTrue();
    assertThat(executor.commands).containsExactly(List.of("make", "verify"));
  }

  @Test
  void disabledGatesAreNotExecuted(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"),
        """
                version = 1
                [quality-gates.tests]
                command = "./mvnw test"
                [quality-gates.sonar]
                enabled = false
                command = "sonar analyze agentic"
                """);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    service(executor).check(dir);

    assertThat(executor.commands).hasSize(1);
  }

  @Test
  void failsWhenNoGateIsEnabled(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"), "version = 1\n[quality-gates.tests]\nenabled = false\n");

    assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("No quality gates are enabled");
  }

  @Test
  void supportsConfiguredAnalysisGate(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"),
        "version = 1\n[quality-gates.sonar]\ncommand = \"sonar analyze agentic\"\n");

    assertThat(service(new FakeCommandExecutor(0, "", "")).check(dir).results())
        .singleElement()
        .extracting(result -> result.name())
        .isEqualTo("sonar");
  }

  @Test
  void failsWithoutProjectOrConfiguration(@TempDir final Path dir) {
    assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
        .isInstanceOf(ProjectNotFoundException.class);

    withPom(dir, PLAIN_POM);
    assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
        .hasMessageContaining("ward init");
  }

  @Test
  void selectsUnionOnceInDeclarationOrderAndDefaultsToDefault(@TempDir final Path dir)
      throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(
        dir.resolve("ward.toml"),
        """
            version = 1
            [quality-gates.tests]
            command = "first"
            profiles = ["a", "b"]
            [quality-gates.coverage]
            command = "defaulted"
            [quality-gates.compile]
            command = "second"
            profiles = ["b"]
            """);
    final List<String> executed = new ArrayList<>();
    final CommandExecutor executor =
        (command, root) -> {
          executed.add(command.getFirst());
          return new CommandResult(0, "", "", Duration.ZERO);
        };
    final CheckService checks = CheckServiceFixtures.checks(executor);

    checks.check(dir, List.of("a", "b"));
    assertThat(executed).containsExactly("first", "second");
    executed.clear();
    checks.check(dir);
    assertThat(executed).containsExactly("defaulted");
    executed.clear();
    checks.check(dir, List.of());
    assertThat(executed).containsExactly("defaulted");
    executed.clear();
    checks.check(dir, null);
    assertThat(executed).containsExactly("defaulted");
    assertThatThrownBy(() -> checks.check(dir, List.of("missing", "unknown")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("missing")
        .hasMessageContaining("unknown");
    assertThatThrownBy(() -> checks.check(dir, List.of("a", " ")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("must not be blank");
    Files.writeString(
        dir.resolve("ward.toml"),
        "version = 1\n[quality-gates.compile]\nenabled = false\ncommand = 'unused'\nprofiles = ['disabled']\n");
    executed.clear();
    assertThatThrownBy(() -> checks.check(dir, List.of("disabled")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("No quality gates are enabled");
    assertThat(executed).isEmpty();
  }
}
