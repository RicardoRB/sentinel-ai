package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.ProjectFixtures;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.doctor.OpenSpecIgnoreWarning;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init still writes an OpenSpec gate whose directory Git ignores, but says CI will not see it. */
class InitOpenSpecIgnoreWarningTest {
  @TempDir Path root;

  @Test
  void warnsAndStillWritesTheGateWhenDirectoryIsIgnored() throws IOException {
    final InitResult result = initialize(true, List.of("openspec"));

    assertThat(result.pomWarnings()).contains(OpenSpecIgnoreWarning.MESSAGE);
    assertThat(Files.readString(root.resolve("sentinel.toml")))
        .contains("[quality-gates.openspec]");
  }

  @Test
  void staysQuietWhenDirectoryIsTracked() {
    assertThat(initialize(false, List.of("openspec")).pomWarnings())
        .doesNotContain(OpenSpecIgnoreWarning.MESSAGE);
  }

  @Test
  void staysQuietWithoutOpenSpecValidation() {
    assertThat(initialize(true, List.of()).pomWarnings())
        .doesNotContain(OpenSpecIgnoreWarning.MESSAGE);
  }

  private InitResult initialize(final boolean ignored, final List<String> validations) {
    ProjectFixtures.withPom(root, ProjectFixtures.PLAIN_POM);
    final FakeEnvironmentInspection environment =
        new FakeEnvironmentInspection() {
          @Override
          public boolean isOpenSpecDirectoryIgnored(final Path projectRoot) {
            return ignored;
          }
        };
    return new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            new InitSetupCatalog(environment),
            new InitProjectFiles(
                new PomToolConfigurator(),
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()))
        .initialize(
            root,
            new InitSelection(
                List.of("none"),
                List.of("tests"),
                null,
                null,
                validations.isEmpty() ? List.of() : List.of("openspec"),
                validations,
                List.of(),
                false));
  }
}
