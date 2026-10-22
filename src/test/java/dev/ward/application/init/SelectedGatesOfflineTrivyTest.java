package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Trivy resolves dependencies offline once an earlier build-tool gate has filled the cache. */
class SelectedGatesOfflineTrivyTest {
  private static final Path ROOT = Path.of("/project");
  private static final List<String> TRIVY = List.of("trivy", "fs", "--exit-code", "1", ".");
  private static final List<String> OFFLINE =
      List.of("trivy", "fs", "--offline-scan", "--exit-code", "1", ".");
  private static final InitGateOption TRIVY_GATE =
      new InitGateOption("trivy", "trivy", TRIVY, true, "available");

  @ParameterizedTest
  @CsvSource({"MAVEN, true, ./mvnw", "MAVEN, false, mvn", "GRADLE, true, ./gradlew"})
  void buildGateBeforeTrivyMakesItOffline(
      final BuildTool tool, final boolean wrapper, final String executable) {
    assertThat(trivyCommand(project(tool, wrapper), gate("tests", executable), TRIVY_GATE))
        .isEqualTo(OFFLINE);
  }

  @Test
  void trivyAloneStaysOnline() {
    assertThat(trivyCommand(project(BuildTool.MAVEN, true), TRIVY_GATE)).isEqualTo(TRIVY);
  }

  @Test
  void buildGateAfterTrivyKeepsItOnline() {
    final List<InitGateOption> gates =
        SelectedGates.offlineTrivy(
            List.of(TRIVY_GATE, gate("tests", "./mvnw")), project(BuildTool.MAVEN, true));

    assertThat(gates.getFirst().command()).isEqualTo(TRIVY);
    assertThat(gates).extracting(InitGateOption::id).containsExactly("trivy", "tests");
  }

  @Test
  void nonJvmProjectStaysOnline() {
    assertThat(trivyCommand(project(BuildTool.NPM, false), gate("tests", "npm"), TRIVY_GATE))
        .isEqualTo(TRIVY);
  }

  @Test
  void generatedConfigurationContainsOfflineTrivy(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            new InitProjectFiles(
                new PomToolConfigurator(),
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()))
        .initialize(dir, new InitSelection(List.of("none"), List.of("tests", "trivy")));

    assertThat(Files.readString(dir.resolve("ward.toml")))
        .contains("command = \"trivy fs --offline-scan --exit-code 1 .\"");
  }

  private static List<String> trivyCommand(final Project project, final InitGateOption... gates) {
    return SelectedGates.offlineTrivy(List.of(gates), project).stream()
        .filter(gate -> "trivy".equals(gate.id()))
        .findFirst()
        .orElseThrow()
        .command();
  }

  private static Project project(final BuildTool tool, final boolean wrapper) {
    return new Project(ROOT, Language.JAVA, tool, Framework.NONE, wrapper);
  }

  private static InitGateOption gate(final String id, final String executable) {
    return new InitGateOption(id, id, List.of(executable, "test"), true, "available");
  }
}
