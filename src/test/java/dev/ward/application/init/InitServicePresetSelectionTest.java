package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitResult;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
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

/** The selected gate list is final: presets order it but never add unselected gates back. */
class InitServicePresetSelectionTest {
  private static final String FORMAT = "format";
  private static final String WARD_TOML = "ward.toml";

  private final InitService service =
      new InitService(
          new ProjectDetector(new FileSystemProjectInspection()),
          Set.of(),
          new InitSetupCatalog(new FakeEnvironmentInspection()),
          new InitProjectFiles(
              new PomToolConfigurator(),
              new PresetRuleFileWriter(),
              new ArchitectureTestGenerator(),
              new FileConfigurationStorage()));

  @Test
  void ordersSelectedPresetGatesBeforeOtherGates(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final InitResult result =
        service.initialize(
            dir,
            new InitSelection(
                List.of("none"),
                List.of(
                    "gitleaks",
                    "tests",
                    "compile",
                    "coverage",
                    "spotbugs",
                    "checkstyle",
                    "pmd",
                    FORMAT),
                null,
                QualityPreset.STANDARD));
    assertThat(result.gates())
        .extracting(InitGateOption::id)
        .containsExactly(
            "compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage", "gitleaks");
    assertThat(Files.readString(dir.resolve(WARD_TOML)))
        .contains("version = 1\npreset = \"standard\"");
    assertThat(dir.resolve("config/pmd-ruleset.xml")).exists();
  }

  @Test
  void unselectedPresetGateIsNotConfigured(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final List<String> strictWithoutMutation =
        JavaPresetGates.STRICT.stream().filter(gate -> !"mutation".equals(gate)).toList();

    final InitResult result =
        service.initialize(
            dir,
            new InitSelection(
                List.of("none"), strictWithoutMutation, "layered", QualityPreset.STRICT));

    assertThat(result.gates())
        .extracting(InitGateOption::id)
        .containsExactlyElementsOf(strictWithoutMutation);
    assertThat(Files.readString(dir.resolve(WARD_TOML)))
        .contains("preset = \"strict\"")
        .doesNotContain("[quality-gates.mutation]");
    assertThat(Files.readString(dir.resolve("pom.xml"))).doesNotContain("pitest-maven");
  }

  @Test
  void unselectedPresetGateWritesNoRuleFile(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final List<String> standardWithoutPmd =
        JavaPresetGates.STANDARD.stream().filter(gate -> !"pmd".equals(gate)).toList();

    service.initialize(
        dir, new InitSelection(List.of("none"), standardWithoutPmd, null, QualityPreset.STANDARD));

    assertThat(Files.readString(dir.resolve(WARD_TOML))).doesNotContain("[quality-gates.pmd]");
    assertThat(dir.resolve("config/pmd-ruleset.xml")).doesNotExist();
    assertThat(dir.resolve("config/checkstyle.xml")).exists();
  }
}
