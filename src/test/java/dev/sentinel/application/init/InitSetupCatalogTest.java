package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.ProjectFixtures;
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
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitSetupCatalogTest {
  private static final String OPENSPEC = "openspec";
  private static final String OPENSPEC_ARCHIVED = "openspec-archived";
  private static final String COMPILE = "compile";
  private static final String TESTS = "tests";

  @Test
  void exposesStableIntegrationAndGateChoices(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(catalog.integrations())
        .extracting(InitIntegrationOption::id)
        .containsExactly("none", "opencode", "claude-code");
    assertThat(
            catalog.gates(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow()))
        .extracting(InitGateOption::id)
        .doesNotContain(OPENSPEC, OPENSPEC_ARCHIVED);
    assertThat(QualityGateFactory.SUPPORTED_GATES).contains(OPENSPEC, OPENSPEC_ARCHIVED);
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
        .containsExactly(COMPILE, TESTS, "format", "checkstyle", "pmd", "spotbugs", "coverage");
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
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    Files.writeString(dir.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection())
            .gate(
                new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow(),
                TESTS);

    assertThat(option.command()).containsExactly("./mvnw", "test");
    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: ./mvnw");
  }

  @Test
  void rejectsUnsupportedChoices(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
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
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection()).gate(project, TESTS);

    assertThat(option.available()).isFalse();
    assertThat(option.availabilityMessage()).contains("Maven is unavailable");
  }

  @Test
  void reportsSystemMavenWhenWrapperIsMissing(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitGateOption option =
        new InitSetupCatalog(new FakeEnvironmentInspection("mvn")).gate(project, TESTS);

    assertThat(option.available()).isTrue();
    assertThat(option.availabilityMessage()).contains("Maven command available: mvn");
  }

  @Test
  void usesGradleWrapperAndGradleTasks(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    Files.writeString(dir.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
    Files.setPosixFilePermissions(
        dir.resolve("gradlew"), PosixFilePermissions.fromString("rwxr-xr-x"));
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());

    assertThat(catalog.gate(project, TESTS).command()).containsExactly("./gradlew", "test");
    assertThat(catalog.gate(project, COMPILE).command()).containsExactly("./gradlew", "classes");
    assertThat(catalog.gate(project, "format").command()).endsWith("spotlessCheck");
    assertThat(catalog.gate(project, TESTS).availabilityMessage())
        .contains("Gradle command available: ./gradlew");
  }

  @Test
  void usesSystemGradleWhenWrapperIsMissing(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle.kts"), "plugins { java }\n");
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();

    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection("gradle"));

    assertThat(catalog.gate(project, TESTS).command()).containsExactly("gradle", "test");
    assertThat(catalog.gate(project, TESTS).available()).isTrue();
  }

  @Test
  void offersFullGateCatalogForGradle(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle"), "plugins { id 'java' }\n");
    final Project project =
        new ProjectDetector(new FileSystemProjectInspection()).detect(dir).orElseThrow();
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection("gradle"));

    assertThat(catalog.gates(project))
        .extracting(InitGateOption::id)
        .containsExactlyInAnyOrderElementsOf(
            QualityGateFactory.SUPPORTED_GATES.stream()
                .filter(id -> !OPENSPEC.equals(id) && !OPENSPEC_ARCHIVED.equals(id))
                .toList());
    assertThat(catalog.gate(project, "coverage").command())
        .containsExactly("gradle", "jacocoTestReport");
    assertThat(catalog.gate(project, "dependency-check").command())
        .containsExactly("gradle", "dependencyCheckAnalyze");
  }

  @Test
  void offersBinaryGatesWithPlaceholderTargetAndHints(@TempDir final Path dir) throws Exception {
    ProjectFixtures.withPom(dir, ProjectFixtures.PLAIN_POM);
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
