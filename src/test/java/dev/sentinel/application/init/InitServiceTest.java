package dev.sentinel.application.init;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitServiceTest {

  private final InitService service =
      new InitService(
          new ProjectDetector(new FileSystemProjectInspection()),
          Set.of(),
          new InitSetupCatalog(new FakeEnvironmentInspection()),
          new PomToolConfigurator(),
          new ArchitectureTestGenerator(),
          new FileConfigurationStorage());

  @Test
  void createsDefaultConfiguration(final @TempDir Path dir) throws Exception {
    withPom(dir, PLAIN_POM);

    final InitResult result = service.init(dir);

    assertThat(result.created()).isTrue();
    assertThat(Files.readString(dir.resolve("sentinel.toml")))
        .isEqualTo(
            """
                version = 1

                [quality-gates.tests]
                enabled = true
                command = "./mvnw test"
                """);
  }

  @Test
  void neverOverwritesExistingConfiguration(final @TempDir Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("sentinel.toml"), "version = 1 # mine\n");

    final InitResult result = service.init(dir);

    assertThat(result.created()).isFalse();
    assertThat(Files.readString(dir.resolve("sentinel.toml"))).isEqualTo("version = 1 # mine\n");
  }

  @Test
  void requiresASupportedProject(final @TempDir Path dir) {
    assertThatThrownBy(() -> service.init(dir)).isInstanceOf(ProjectNotFoundException.class);
  }
}
