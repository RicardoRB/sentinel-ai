package dev.sentinel.application.gate;


import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.gate.QualityGateRunner;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckServiceTest {

    @Test
    void orchestratesWithInMemoryProjectAndConfigurationPorts() {
        Path root = Path.of("in-memory-project");
        Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
        ProjectInspection projects = new ProjectInspection() {
            @Override public Optional<Project> detect(Path ignored) { return Optional.of(project); }
            @Override public List<Project> discover(Path ignored) { return List.of(project); }
        };
        FakeCommandExecutor executor = new FakeCommandExecutor(0, "ok", "");
        CheckService checks = new CheckService(new ProjectDetector(projects),
                file -> new SentinelConfiguration(1, Map.of("tests", new GateConfiguration(true,
                        List.of("mvn", "test")))), new QualityGateFactory(executor), new QualityGateRunner());

        CheckReport report = checks.check(root);

        assertThat(report.passed()).isTrue();
        assertThat(executor.commands).containsExactly(List.of("mvn", "test"));
        assertThat(executor.workingDirectories).containsExactly(root);
    }

    private CheckService service(FakeCommandExecutor executor) {
        return new CheckService(new ProjectDetector(new FileSystemProjectInspection()), new TomlConfigurationReader(),
                new QualityGateFactory(executor), new QualityGateRunner());
    }

    @Test
    void runsConfiguredCommandNotAHardcodedOne(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), """
                version = 1
                [quality-gates.tests]
                command = "make verify"
                """);
        FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

        CheckReport report = service(executor).check(dir);

        assertThat(report.passed()).isTrue();
        assertThat(executor.commands).containsExactly(List.of("make", "verify"));
    }

    @Test
    void disabledGatesAreNotExecuted(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), """
                version = 1
                [quality-gates.tests]
                command = "./mvnw test"
                [quality-gates.sonar]
                enabled = false
                command = "sonar analyze agentic"
                """);
        FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

        service(executor).check(dir);

        assertThat(executor.commands).hasSize(1);
    }

    @Test
    void failsWhenNoGateIsEnabled(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1\n[quality-gates.tests]\nenabled = false\n");

        assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
                .isInstanceOf(SentinelException.class).hasMessageContaining("No quality gates are enabled");
    }

    @Test
    void supportsConfiguredAnalysisGate(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"),
                "version = 1\n[quality-gates.sonar]\ncommand = \"sonar analyze agentic\"\n");

        assertThat(service(new FakeCommandExecutor(0, "", "")).check(dir).results())
                .singleElement().extracting(result -> result.name()).isEqualTo("sonar");
    }

    @Test
    void failsWithoutProjectOrConfiguration(@TempDir Path dir) {
        assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
                .isInstanceOf(ProjectNotFoundException.class);

        withPom(dir, PLAIN_POM);
        assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
                .hasMessageContaining("sentinel init");
    }
}
