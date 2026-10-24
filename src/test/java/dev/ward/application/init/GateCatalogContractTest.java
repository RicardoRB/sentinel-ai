package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.QualityGateDefinitions;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.GradleToolConfigurator;
import dev.ward.infrastructure.init.PomToolConfigurator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The checklist for adding a language or a tool: each test names the missing piece. See the "Adding
 * a toolchain, language or tool" section of AGENTS.md.
 */
class GateCatalogContractTest {
  /** JVM languages with gates; every other language must fail instead of borrowing them. */
  private static final Set<Language> JVM_LANGUAGES = Set.of(Language.JAVA, Language.KOTLIN);

  /** Gates that run the build tool as it is and need no build-file entry. */
  private static final Set<String> NO_BUILD_FILE = Set.of("tests", "compile", "command");

  @TempDir Path root;

  @Test
  void everyLanguageHasJvmGatesOrFailsNamingIt() {
    for (final Language language : Language.values()) {
      final Project project = project(BuildTool.GRADLE, language);
      if (JVM_LANGUAGES.contains(language)) {
        assertThat(jvmGates(project)).as("%s gates", language).isNotEmpty();
      } else {
        assertThatThrownBy(() -> jvmGates(project))
            .as("%s must not get another language's gates", language)
            .isInstanceOf(WardException.class)
            .hasMessageContaining(language.displayName());
      }
    }
  }

  @Test
  void everyOfferedGateIsDefined() {
    final InitGeneralCatalog catalog = new InitGeneralCatalog(new FakeEnvironmentInspection());
    assertThat(
            Stream.of(BuildTool.MAVEN, BuildTool.GRADLE)
                .flatMap(buildTool -> catalog.gates(project(buildTool)).stream()))
        .extracting(InitGateOption::id)
        .allSatisfy(
            id ->
                assertThat(QualityGateDefinitions.find(id))
                    .as("gate %s has no QualityGateDefinitions row", id)
                    .isPresent());
  }

  @Test
  void everyJvmGateConfiguresEachBuildTool() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
    Files.writeString(root.resolve("build.gradle"), "plugins {\n    id 'java'\n}\n");
    assertConfigures(BuildTool.MAVEN, new PomToolConfigurator());
    assertConfigures(BuildTool.GRADLE, new GradleToolConfigurator());
  }

  @Test
  void gateKeyedRowsNameDefinedGates() {
    assertThat(
            Stream.concat(
                PartialCheckDefaults.gates().stream(), AgentRulesTemplate.limitGates().stream()))
        .allSatisfy(
            id ->
                assertThat(QualityGateDefinitions.find(id))
                    .as("row for undefined gate %s", id)
                    .isPresent());
  }

  private void assertConfigures(final BuildTool buildTool, final BuildToolConfiguration tools) {
    final Project project = project(buildTool);
    jvmGates(project).stream()
        .filter(gate -> !NO_BUILD_FILE.contains(gate.id()))
        .forEach(
            gate ->
                assertThat(tools.plan(project, List.of(gate), null, true).action())
                    .as("gate %s has no %s build-file entry", gate.id(), buildTool.displayName())
                    .isEqualTo(PlannedFileChange.Action.MODIFY));
  }

  private static List<InitGateOption> jvmGates(final Project project) {
    final ToolchainInitSupport.Gates gates =
        new JvmInitSupport().gates(project, new FakeEnvironmentInspection());
    return Stream.concat(gates.analysis().stream(), gates.policy().stream()).toList();
  }

  private Project project(final BuildTool buildTool) {
    return project(buildTool, Language.JAVA);
  }

  private Project project(final BuildTool buildTool, final Language language) {
    // Publishing makes Maven offer api-compat, so every JVM gate is listed.
    return new Project(root, language, buildTool, Framework.NONE, false, true, root, Set.of());
  }
}
