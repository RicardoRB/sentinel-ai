package dev.sentinel.application.init;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitSetupCatalogPreviousChoicesTest {
  private final InitSetupCatalog catalog =
      new InitSetupCatalog(
          new FakeEnvironmentInspection(),
          new NoQualityHookGeneration(),
          new TomlConfigurationReader());
  private final InitService service =
      new InitService(
          new ProjectDetector(new FileSystemProjectInspection()),
          Set.of(),
          catalog,
          new InitProjectFiles(
              new PomToolConfigurator(),
              new ArchitectureTestGenerator(),
              new FileConfigurationStorage()));

  @Test
  void strictConfigurationGivesItsPresetAndGatesInOrder(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    service.initialize(
        dir,
        new InitSelection(
            List.of("none"), QualityPreset.STRICT.gates(), "layered", QualityPreset.STRICT));

    assertThat(catalog.previousChoices(project(dir)))
        .hasValue(new PreviousChoices(QualityPreset.STRICT, QualityPreset.STRICT.gates()));
  }

  @Test
  void customConfigurationHasNoPreset(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    service.initialize(dir, new InitSelection(List.of("none"), List.of("tests", "gitleaks")));

    assertThat(catalog.previousChoices(project(dir)))
        .hasValue(new PreviousChoices(null, List.of("tests", "gitleaks")));
  }

  @Test
  void openSpecValidationsAreNotOfferedAsGates(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    service.initialize(
        dir,
        new InitSelection(
            List.of("none"),
            List.of("tests"),
            null,
            null,
            List.of("openspec"),
            List.of("openspec"),
            List.of(),
            false));

    assertThat(catalog.previousChoices(project(dir)))
        .hasValue(new PreviousChoices(null, List.of("tests")));
  }

  @Test
  void unreadableConfigurationGivesNoChoices(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("sentinel.toml"), "not [valid toml");

    assertThat(catalog.previousChoices(project(dir))).isEmpty();
  }

  private Project project(final Path dir) {
    return service.project(dir);
  }
}
