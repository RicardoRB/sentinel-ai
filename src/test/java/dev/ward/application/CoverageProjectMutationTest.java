package dev.ward.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectFixtures;
import dev.ward.application.gate.CommandQualityGate;
import dev.ward.application.gate.MavenTestGate;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.SkippedQualityGate;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.PomToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// This test intentionally aggregates coverage across the application services.
// Splitting it would duplicate setup and obscure the cross-service scenarios.
class CoverageProjectMutationTest {
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void pomConfiguratorAddsAndRollsBackAllTools(@TempDir final Path root) throws IOException {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final List<InitGateOption> gates =
        catalog.gates(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE, false, true, root));
    final PomToolConfigurator configurator = new PomToolConfigurator();
    final BuildFileChange change =
        configurator.apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), gates);
    assertThat(change.tools())
        .containsExactly(
            "maven-checkstyle-plugin",
            "maven-pmd-plugin",
            "spotbugs-maven-plugin",
            "sonar-maven-plugin",
            "jacoco-maven-plugin",
            "dependency-check-maven",
            "archunit-junit5",
            "pitest-maven",
            "maven-enforcer-plugin",
            "spotless-maven-plugin",
            "license-maven-plugin",
            "japicmp-maven-plugin");
    assertThat(Files.readString(root.resolve("pom.xml")))
        .contains(
            "jacoco-maven-plugin",
            "maven-checkstyle-plugin",
            "spotbugs-maven-plugin",
            "sonar-maven-plugin",
            "dependency-check-maven",
            "archunit-junit5",
            "pitest-maven",
            "maven-enforcer-plugin");
    configurator.rollback(change);
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(ProjectFixtures.PLAIN_POM);
    configurator.rollback(null);
    assertThat(new BuildFileChange(Toolchain.JVM, root.resolve("pom.xml"), "", List.of()).changed())
        .isFalse();
  }

  @Test
  void presetPomSnippetsUseExternalRuleFilesAndStrictMutation(@TempDir final Path root)
      throws IOException {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final List<InitGateOption> gates =
        catalog.gates(project).stream()
            .filter(gate -> QualityPreset.STRICT.gates().contains(gate.id()))
            .toList();
    final BuildFileChange change =
        new PomToolConfigurator().apply(project, gates, QualityPreset.STRICT);
    final String pom = Files.readString(root.resolve("pom.xml"));
    assertThat(pom)
        .contains("${project.basedir}/config/enforcer-rules.xml", "mutationThreshold>60");
    assertThat(change.tools()).contains("maven-pmd-plugin");
    new PomToolConfigurator().rollback(change);
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(ProjectFixtures.PLAIN_POM);
  }

  @Test
  void pomConfiguratorHandlesExistingBuildSections(@TempDir final Path root) throws IOException {
    final String pom =
        "<project><build><plugins></plugins></build><dependencies></dependencies></project>";
    Files.writeString(root.resolve("pom.xml"), pom);
    final InitSetupCatalog catalog = new InitSetupCatalog(new FakeEnvironmentInspection());
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final BuildFileChange change =
        new PomToolConfigurator()
            .apply(
                project,
                List.of(catalog.gate(project, "checkstyle"), catalog.gate(project, "archunit")));
    assertThat(change.changed()).isTrue();
    assertThat(Files.readString(root.resolve("pom.xml"))).contains("<plugins>", "<dependencies>");
  }

  @Test
  void gateDomainCoversPassFailureSkippedAndExecutionError() {
    final CommandExecutor executor =
        (command, root) ->
            new CommandResult(
                "fail".equals(command.getFirst()) ? 1 : 0, "out", "err", Duration.ofMillis(2));
    assertThat(new CommandQualityGate("custom", executor, List.of("ok")).execute(PROJECT).status())
        .isEqualTo(GateStatus.PASSED);
    assertThat(
            new CommandQualityGate("custom", executor, List.of("fail")).execute(PROJECT).status())
        .isEqualTo(GateStatus.FAILED);
    assertThat(new MavenTestGate(executor, List.of("test")).name()).isEqualTo("tests");
    assertThat(new SkippedQualityGate("skip").execute(PROJECT).status())
        .isEqualTo(GateStatus.SKIPPED);
    assertThat(
            GateResult.builder("x", GateStatus.PASSED)
                .command(List.of("x"))
                .exitCode(0)
                .duration(Duration.ZERO)
                .stdout("")
                .stderr("")
                .build()
                .passed())
        .isTrue();
  }
}
