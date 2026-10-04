package dev.sentinel.application;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.infrastructure.TomlConfigurationReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckServiceTest {

    private CheckService service(FakeCommandExecutor executor) {
        return new CheckService(new ProjectDetector(), new TomlConfigurationReader(),
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
        assertThat(executor.commands).containsExactly(java.util.List.of("make", "verify"));
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
    void failsOnEnabledUnsupportedGate(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"),
                "version = 1\n[quality-gates.sonar]\ncommand = \"sonar analyze agentic\"\n");

        assertThatThrownBy(() -> service(new FakeCommandExecutor(0, "", "")).check(dir))
                .hasMessageContaining("'sonar'").hasMessageContaining("not supported");
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
