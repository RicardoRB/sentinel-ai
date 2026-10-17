package dev.sentinel.application.init;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The selected gate list is final: presets order it but never add unselected gates back. */
class InitServicePresetSelectionTest {
  private static final String FORMAT = "format";
  private static final String SENTINEL_TOML = "sentinel.toml";

  private final InitService service =
      new InitService(
          new ProjectDetector(new FileSystemProjectInspection()),
          Set.of(),
          new InitSetupCatalog(new FakeEnvironmentInspection()),
          new PomToolConfigurator(),
          new ArchitectureTestGenerator(),
          new FileConfigurationStorage(),
          new PresetRuleFileWriter());

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
    assertThat(Files.readString(dir.resolve(SENTINEL_TOML)))
        .contains("version = 1\npreset = \"standard\"");
    assertThat(dir.resolve("config/pmd-ruleset.xml")).exists();
  }

  @Test
  void unselectedPresetGateIsNotConfigured(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final List<String> strictWithoutMutation =
        QualityPreset.STRICT.gates().stream().filter(gate -> !"mutation".equals(gate)).toList();

    final InitResult result =
        service.initialize(
            dir,
            new InitSelection(
                List.of("none"), strictWithoutMutation, "layered", QualityPreset.STRICT));

    assertThat(result.gates())
        .extracting(InitGateOption::id)
        .containsExactlyElementsOf(strictWithoutMutation);
    assertThat(Files.readString(dir.resolve(SENTINEL_TOML)))
        .contains("preset = \"strict\"")
        .doesNotContain("[quality-gates.mutation]");
    assertThat(Files.readString(dir.resolve("pom.xml"))).doesNotContain("pitest-maven");
  }

  @Test
  void unselectedPresetGateWritesNoRuleFile(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final List<String> standardWithoutPmd =
        QualityPreset.STANDARD.gates().stream().filter(gate -> !"pmd".equals(gate)).toList();

    service.initialize(
        dir, new InitSelection(List.of("none"), standardWithoutPmd, null, QualityPreset.STANDARD));

    assertThat(Files.readString(dir.resolve(SENTINEL_TOML))).doesNotContain("[quality-gates.pmd]");
    assertThat(dir.resolve("config/pmd-ruleset.xml")).doesNotExist();
    assertThat(dir.resolve("config/checkstyle.xml")).exists();
  }
}
