package dev.sentinel.application.init;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
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

class InitServiceTest {

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
  void appliesPresetAndDeduplicatesExplicitGates(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final InitResult result =
        service.initialize(
            dir,
            new InitSelection(
                List.of("none"), List.of("tests", "gitleaks"), null, QualityPreset.STANDARD));
    assertThat(result.gates())
        .extracting(InitGateOption::id)
        .containsExactly(
            "compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage", "gitleaks");
    assertThat(Files.readString(dir.resolve("sentinel.toml")))
        .contains("version = 1\npreset = \"standard\"");
    assertThat(dir.resolve("config/pmd-ruleset.xml")).exists();
  }

  @Test
  void rejectsUnknownExplicitGateBeforeWriting(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    assertThatThrownBy(
            () ->
                service.initialize(
                    dir,
                    new InitSelection(
                        List.of("none"), List.of("nope"), null, QualityPreset.STANDARD)))
        .hasMessageContaining("nope");
    assertThat(dir.resolve("sentinel.toml")).doesNotExist();
    assertThat(Files.readString(dir.resolve("pom.xml"))).isEqualTo(PLAIN_POM);
  }

  @Test
  void integrationConflictLeavesPresetFilesUntouched(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final AgentIntegration conflict =
        new AgentIntegration() {
          @Override
          public String id() {
            return "opencode";
          }

          @Override
          public IntegrationResult integrate(final Path root, final boolean remove) {
            return new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of(), "conflict");
          }
        };
    final InitService conflicting =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(conflict),
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            new PomToolConfigurator(),
            new ArchitectureTestGenerator(),
            new FileConfigurationStorage(),
            new PresetRuleFileWriter());
    assertThatThrownBy(
            () ->
                conflicting.initialize(
                    dir,
                    new InitSelection(
                        List.of("opencode"), List.of(), null, QualityPreset.STANDARD)))
        .hasMessageContaining("conflicts");
    assertThat(dir.resolve("sentinel.toml")).doesNotExist();
    assertThat(dir.resolve("config")).doesNotExist();
    assertThat(Files.readString(dir.resolve("pom.xml"))).isEqualTo(PLAIN_POM);
  }

  @Test
  void createsDefaultConfiguration(@TempDir final Path dir) throws Exception {
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
  void neverOverwritesExistingConfiguration(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve("sentinel.toml"), "version = 1 # mine\n");

    final InitResult result = service.init(dir);

    assertThat(result.created()).isFalse();
    assertThat(Files.readString(dir.resolve("sentinel.toml"))).isEqualTo("version = 1 # mine\n");
  }

  @Test
  void requiresASupportedProject(@TempDir final Path dir) {
    assertThatThrownBy(() -> service.init(dir)).isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void preservesSetupFailureWhenConfigurationRestorationAlsoFails(@TempDir final Path dir)
      throws Exception {
    withPom(dir, PLAIN_POM);
    final InitService failing = InitFailureFixtures.failingService(dir);

    assertThatThrownBy(
            () ->
                failing.initialize(
                    dir, new InitSelection(List.of("none"), List.of("tests"), null, null), true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("setup failed");
  }
}
