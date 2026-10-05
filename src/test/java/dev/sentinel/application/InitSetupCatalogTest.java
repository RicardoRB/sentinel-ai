package dev.sentinel.application;

import dev.sentinel.TestProjects;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitSetupCatalogTest {
    @Test
    void exposesStableIntegrationAndGateChoices(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        InitSetupCatalog catalog = new InitSetupCatalog();

        assertThat(catalog.integrations()).extracting(InitSetupCatalog.IntegrationOption::id)
                .containsExactly("none", "opencode", "claude-code");
        assertThat(catalog.gates(new ProjectDetector().detect(dir).orElseThrow()))
                .extracting(InitSetupCatalog.GateOption::id)
                .containsExactlyElementsOf(QualityGateFactory.SUPPORTED_GATES);
        assertThat(catalog.gates(new ProjectDetector().detect(dir).orElseThrow()))
                .allSatisfy(gate -> assertThat(gate.description()).isNotBlank());
    }

    @Test
    void prefersWrapperAndReportsMissingSystemMaven(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
        InitSetupCatalog.GateOption option = new InitSetupCatalog()
                .gate(new ProjectDetector().detect(dir).orElseThrow(), "tests");

        assertThat(option.command()).containsExactly("./mvnw", "test");
        assertThat(option.available()).isTrue();
    }

    @Test
    void rejectsUnsupportedChoices(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        Project project = new ProjectDetector().detect(dir).orElseThrow();
        InitSetupCatalog catalog = new InitSetupCatalog();

        assertThatThrownBy(() -> catalog.integration("unknown"))
                .isInstanceOf(SentinelException.class);
        assertThatThrownBy(() -> catalog.gate(project, "unknown"))
                .isInstanceOf(SentinelException.class);
    }

    @Test
    void reportsUnavailableMavenWhenWrapperAndSystemToolAreMissing(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        Project project = new ProjectDetector().detect(dir).orElseThrow();

        InitSetupCatalog.GateOption option = new InitSetupCatalog().gate(project, "tests");

        assertThat(option.available()).isFalse();
        assertThat(option.availabilityMessage()).contains("Maven is unavailable");
    }
}
