package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.PartialGateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code partial} tables init writes, read back through the real configuration parser. */
class ConfigurationTemplatePartialTest {
  private static final List<String> JAVA = List.of("src/**/*.java");
  private static final List<String> GRADLE_BUILD = List.of("build.gradle*", "settings.gradle*");
  private static final String CHECKSTYLE = "checkstyle";
  private static final String PMD = "pmd";
  private static final String FORMAT = "format";

  @TempDir Path dir;

  @Test
  void mavenWithPresetNarrowsCheckstyleAndFormatAndRunsPmdInFull() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, QualityPreset.STANDARD);

    assertThat(gates.get(CHECKSTYLE).partial())
        .isEqualTo(
            new PartialGateConfiguration(
                JAVA,
                List.of("config/checkstyle.xml", "pom.xml"),
                List.of("-Dcheckstyle.includes={paths}"),
                "**/{name}",
                ","));
    assertThat(gates.get(PMD).partial())
        .isEqualTo(
            new PartialGateConfiguration(
                JAVA, List.of("config/pmd-ruleset.xml", "pom.xml"), List.of(), null, null));
    assertThat(gates.get(FORMAT).partial())
        .isEqualTo(
            new PartialGateConfiguration(
                JAVA, List.of("pom.xml"), List.of("-DspotlessFiles={paths}"), ".*/{path}", ","));
  }

  @Test
  void mavenWithoutPresetListsOnlyTheBuildFileAsConfiguration() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, null);

    assertThat(gates.get(CHECKSTYLE).partial().config()).containsExactly("pom.xml");
    assertThat(gates.get(CHECKSTYLE).partial().args())
        .containsExactly("-Dcheckstyle.includes={paths}");
    assertThat(gates.get(PMD).partial().config()).containsExactly("pom.xml");
  }

  @Test
  void gradleWithPresetNarrowsCheckstyleAndPmdThroughTheBuildFilter() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.GRADLE, QualityPreset.STRICT);

    for (final String gate : List.of(CHECKSTYLE, PMD)) {
      assertThat(gates.get(gate).partial().args()).containsExactly("-PwardPaths={paths}");
      assertThat(gates.get(gate).partial().separator()).isEqualTo(",");
      assertThat(gates.get(gate).partial().item()).isEqualTo("{path}");
      assertThat(gates.get(gate).partial().config()).containsAll(GRADLE_BUILD);
    }
    assertThat(gates.get(FORMAT).partial())
        .isEqualTo(
            new PartialGateConfiguration(JAVA, List.of("build.gradle*"), List.of(), null, null));
  }

  @Test
  void gradleWithoutPresetRunsCheckstyleAndPmdInFullBecauseNoFilterIsWritten() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.GRADLE, null);

    assertThat(gates.get(CHECKSTYLE).partial().args()).isEmpty();
    assertThat(gates.get(PMD).partial().args()).isEmpty();
    assertThat(gates.get(CHECKSTYLE).partial().config()).isEqualTo(GRADLE_BUILD);
  }

  @Test
  void semgrepPassesEachPathAsItsOwnArgument() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, QualityPreset.STANDARD);

    assertThat(gates.get("semgrep").partial())
        .isEqualTo(
            new PartialGateConfiguration(
                List.of("**"), List.of(".semgrep*"), List.of("{paths}"), null, null));
  }

  @Test
  void gatesThatDependOnTheWholeProjectHaveNoPartialTable() throws IOException {
    final Map<String, GateConfiguration> gates = parse(BuildTool.MAVEN, QualityPreset.STANDARD);

    for (final String gate :
        List.of("compile", "tests", "coverage", "archunit", "mutation", "spotbugs", "gitleaks")) {
      assertThat(gates.get(gate).partial()).as(gate).isNull();
    }
  }

  private Map<String, GateConfiguration> parse(
      final BuildTool buildTool, final QualityPreset preset) throws IOException {
    final List<InitGateOption> options =
        List.of(
                "compile",
                "tests",
                FORMAT,
                CHECKSTYLE,
                PMD,
                "spotbugs",
                "coverage",
                "archunit",
                "mutation",
                "gitleaks",
                "semgrep")
            .stream()
            .map(id -> new InitGateOption(id, id, List.of("tool", id), true, "available"))
            .toList();
    final Path file = dir.resolve(WardConfiguration.FILE_NAME);
    Files.writeString(
        file,
        ConfigurationTemplate.render(
            options, preset, new Project(dir, Language.JAVA, buildTool, Framework.NONE)));
    return new TomlConfigurationReader().read(file).gates();
  }
}
