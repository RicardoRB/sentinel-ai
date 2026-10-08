package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.TestProjects;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitSetupCatalogTest {
  @Test
  void exposesStableIntegrationAndGateChoices(@TempDir Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    InitSetupCatalog catalog = new InitSetupCatalog(new SystemEnvironmentInspection());

    assertThat(catalog.integrations())
        .extracting(InitIntegrationOption::id)
        .containsExactly("none", "opencode", "claude-code");
    assertThat(
            catalog.gates(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow()))
        .extracting(InitGateOption::id)
        .containsExactlyElementsOf(QualityGateFactory.SUPPORTED_GATES);
    assertThat(
            catalog.gates(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow()))
        .allSatisfy(gate -> assertThat(gate.description()).isNotBlank());
  }

  @Test
  void prefersWrapperAndReportsMissingSystemMaven(@TempDir Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
    InitGateOption option =
        new InitSetupCatalog(new SystemEnvironmentInspection())
            .gate(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow(),
                "tests");

    assertThat(option.command()).containsExactly("./mvnw", "test");
    assertThat(option.available()).isTrue();
  }

  @Test
  void rejectsUnsupportedChoices(@TempDir Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    InitSetupCatalog catalog = new InitSetupCatalog(new SystemEnvironmentInspection());

    assertThatThrownBy(() -> catalog.integration("unknown")).isInstanceOf(SentinelException.class);
    assertThatThrownBy(() -> catalog.gate(project, "unknown"))
        .isInstanceOf(SentinelException.class);
  }

  @Test
  void reportsUnavailableMavenWhenWrapperAndSystemToolAreMissing(@TempDir Path dir)
      throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    InitGateOption option = new InitSetupCatalog(withExecutables()).gate(project, "tests");

    assertThat(option.available()).isFalse();
    assertThat(option.availabilityMessage()).contains("Maven is unavailable");
  }

  @Test
  void reportsSystemMavenWhenWrapperIsMissing(@TempDir Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    InitGateOption option = new InitSetupCatalog(withExecutables("mvn")).gate(project, "tests");

    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: mvn");
  }

  private static EnvironmentInspection withExecutables(String... executables) {
    return root -> new EnvironmentFacts(Set.of(executables), Map.of());
  }

  @Test
  void offersBinaryGatesWithPlaceholderTargetAndHints(@TempDir Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    InitSetupCatalog catalog = new InitSetupCatalog(new SystemEnvironmentInspection());

    assertThat(catalog.gate(project, "zap").command()).contains("-t", "<TARGET_URL>");
    assertThat(catalog.gate(project, "gitleaks").command()).contains("--redact");
    assertThat(catalog.gate(project, "enforcer").command()).endsWith("enforcer:enforce");
    assertThat(catalog.gate(project, "format").command()).endsWith("spotless:check");
    assertThat(catalog.gate(project, "api-compat").command()).endsWith("japicmp:cmp");
    assertThat(catalog.gate(project, "semgrep").availabilityMessage()).isNotBlank();
  }
}
