package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
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
            List.of("none"), JavaPresetGates.STRICT, "layered", QualityPreset.STRICT));

    assertThat(catalog.previousChoices(project(dir)))
        .hasValue(new PreviousChoices(QualityPreset.STRICT, JavaPresetGates.STRICT));
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
            false,
            null,
            false));

    assertThat(catalog.previousChoices(project(dir)))
        .hasValue(new PreviousChoices(null, List.of("tests")));
  }

  @Test
  void unreadableConfigurationGivesNoChoices(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("ward.toml"), "not [valid toml");

    assertThat(catalog.previousChoices(project(dir))).isEmpty();
  }

  private Project project(final Path dir) {
    return service.project(dir);
  }
}
