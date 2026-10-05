package dev.sentinel.application.init;


import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.application.init.InitSetupCatalog;

import dev.sentinel.TestProjects;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.project.Project;
import org.junit.jupiter.api.Test;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.application.gate.QualityGateFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitSetupCatalogTest {
    @Test
    void exposesStableIntegrationAndGateChoices(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        InitSetupCatalog catalog = new InitSetupCatalog(new SystemEnvironmentInspection());

        assertThat(catalog.integrations()).extracting(InitIntegrationOption::id)
                .containsExactly("none", "opencode", "claude-code");
        assertThat(catalog.gates(new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow()))
                .extracting(InitGateOption::id)
                .containsExactlyElementsOf(QualityGateFactory.SUPPORTED_GATES);
        assertThat(catalog.gates(new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow()))
                .allSatisfy(gate -> assertThat(gate.description()).isNotBlank());
    }

    @Test
    void prefersWrapperAndReportsMissingSystemMaven(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
        InitGateOption option = new InitSetupCatalog(new SystemEnvironmentInspection())
                .gate(new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow(), "tests");

        assertThat(option.command()).containsExactly("./mvnw", "test");
        assertThat(option.available()).isTrue();
    }

    @Test
    void rejectsUnsupportedChoices(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        Project project = new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
        InitSetupCatalog catalog = new InitSetupCatalog(new SystemEnvironmentInspection());

        assertThatThrownBy(() -> catalog.integration("unknown"))
                .isInstanceOf(SentinelException.class);
        assertThatThrownBy(() -> catalog.gate(project, "unknown"))
                .isInstanceOf(SentinelException.class);
    }

    @Test
    void reportsUnavailableMavenWhenWrapperAndSystemToolAreMissing(@TempDir Path dir) throws Exception {
        TestProjects.withPom(dir, TestProjects.PLAIN_POM);
        Project project = new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

        InitGateOption option = new InitSetupCatalog(new SystemEnvironmentInspection()).gate(project, "tests");

        assertThat(option.available()).isFalse();
        assertThat(option.availabilityMessage()).contains("Maven is unavailable");
    }
}
