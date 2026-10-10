package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.TestProjects;
import dev.sentinel.application.gate.QualityGateFactory;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitIntegrationOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitSetupCatalogTest {
  @Test
  void exposesStableIntegrationAndGateChoices(@TempDir final Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

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
  void exposesPresetRules() {
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    assertThat(catalog.presets()).containsExactly(QualityPreset.STANDARD, QualityPreset.STRICT);
    assertThat(QualityPreset.STANDARD.gates())
        .containsExactly("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage");
    assertThat(QualityPreset.STRICT.rules().jacocoMinimums())
        .containsEntry("LINE", 0.85)
        .containsEntry("BRANCH", 0.75);
    assertThat(QualityPreset.STANDARD.rules().pmdRules())
        .containsExactly("category/java/quickstart.xml");
    assertThat(QualityPreset.STRICT.rules().pmdRules())
        .anySatisfy(rule -> assertThat(rule).contains("bestpractices"));
    assertThat(QualityPreset.STANDARD.rules().checkstyleModules())
        .containsExactly(
            "EqualsHashCode",
            "MissingSwitchDefault",
            "FallThrough",
            "EmptyCatchBlock",
            "IllegalCatch",
            "AvoidStarImport",
            "UnusedImports",
            "StringLiteralEquality",
            "OneStatementPerLine",
            "MultipleVariableDeclarations");
    assertThat(QualityPreset.STANDARD.rules().spotbugsEffort()).isEqualTo("Default");
    assertThat(QualityPreset.STANDARD.rules().spotbugsThreshold()).isEqualTo("Medium");
    assertThat(QualityPreset.STANDARD.rules().spotbugsExcludes())
        .containsExactly("EI_EXPOSE_REP", "EI_EXPOSE_REP2");
    assertThat(QualityPreset.STRICT.rules().enforcerRules())
        .contains("requireUpperBoundDeps", "requirePluginVersions", "banDynamicVersions");
    assertThat(QualityPreset.STRICT.rules().mutationThreshold()).isEqualTo(60);
    assertThat(QualityPreset.STRICT.rules().architectureExtras().cycles()).isTrue();
    assertThatThrownBy(() -> catalog.preset("paranoid")).hasMessageContaining("standard", "strict");
  }

  @Test
  void prefersWrapperWhenSystemMavenIsMissing(@TempDir final Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection())
            .gate(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow(),
                "tests");

    assertThat(option.command()).containsExactly("./mvnw", "test");
    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: ./mvnw");
  }

  @Test
  void rejectsUnsupportedChoices(@TempDir final Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThatThrownBy(() -> catalog.integration("unknown")).isInstanceOf(SentinelException.class);
    assertThatThrownBy(() -> catalog.gate(project, "unknown"))
        .isInstanceOf(SentinelException.class);
  }

  @Test
  void reportsUnavailableMavenWhenWrapperAndSystemToolAreMissing(@TempDir final Path dir)
      throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection()).gate(project, "tests");

    assertThat(option.available()).isFalse();
    assertThat(option.availabilityMessage()).contains("Maven is unavailable");
  }

  @Test
  void reportsSystemMavenWhenWrapperIsMissing(@TempDir final Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection("mvn")).gate(project, "tests");

    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: mvn");
  }

  @Test
  void offersBinaryGatesWithPlaceholderTargetAndHints(@TempDir final Path dir) throws Exception {
    TestProjects.withPom(dir, TestProjects.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(catalog.gate(project, "zap").command()).contains("-t", "<TARGET_URL>");
    assertThat(catalog.gate(project, "gitleaks").command()).contains("--redact");
    assertThat(catalog.gate(project, "enforcer").command()).endsWith("enforcer:enforce");
    assertThat(catalog.gate(project, "format").command()).endsWith("spotless:check");
    assertThat(catalog.gate(project, "api-compat").command()).endsWith("japicmp:cmp");
    assertThat(catalog.gate(project, "semgrep").availabilityMessage()).isNotBlank();
  }
}
