package dev.ward.application.init;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.init.InitResult;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.QualityHookInstaller;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitServiceTest {
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
  void generatedConfigurationAssignsCumulativeProfiles(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);

    service.initialize(
        dir, new InitSelection(List.of("none"), List.of(FORMAT, "tests", "mutation"), null, null));

    final WardConfiguration configuration =
        new TomlConfigurationReader().read(dir.resolve(WARD_TOML));
    assertThat(configuration.gates().get(FORMAT).profiles())
        .containsExactly("default", "pre-commit", "pre-push", "ci");
    assertThat(configuration.gates().get("tests").profiles()).containsExactly("pre-push", "ci");
    assertThat(configuration.gates().get("mutation").profiles()).containsExactly("ci");
    assertThat(configuration.forProfiles(List.of()).gates()).containsOnlyKeys(FORMAT);
    assertThat(configuration.forProfiles(List.of("pre-commit")).gates()).containsOnlyKeys(FORMAT);
    assertThat(configuration.forProfiles(List.of("pre-push")).gates())
        .containsOnlyKeys("format", "tests");
    assertThat(configuration.forProfiles(List.of("ci")).gates())
        .containsOnlyKeys("format", "tests", "mutation");
  }

  @Test
  void initializesCustomProfilesAndOptInHooks(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.createDirectories(dir.resolve(".git/hooks"));
    final InitService qualityService =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            new InitSetupCatalog(new FakeEnvironmentInspection(), new QualityHookInstaller()),
            new InitProjectFiles(
                new PomToolConfigurator(),
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()),
            new UnavailableToolInstaller());

    qualityService.initialize(
        dir,
        new InitSelection(
            List.of("none"),
            List.of(FORMAT, "mutation"),
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            true,
            null,
            false));

    final String configuration = Files.readString(dir.resolve(WARD_TOML));
    assertThat(configuration)
        .contains("profiles = [\"default\", \"pre-commit\", \"pre-push\", \"ci\"]")
        .contains("profiles = [\"ci\"]");
    assertThat(Files.readString(dir.resolve(".git/hooks/pre-commit")))
        .contains("ward check --profile pre-commit --format json");
    assertThat(Files.readString(dir.resolve(".git/hooks/pre-push")))
        .contains("ward check --profile pre-push --format json");
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
    assertThat(dir.resolve(WARD_TOML)).doesNotExist();
    assertThat(Files.readString(dir.resolve("pom.xml"))).isEqualTo(PLAIN_POM);
  }

  @Test
  void integrationConflictLeavesPresetFilesUntouched(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    final AgentIntegration conflict =
        new AgentIntegration() {
          @Override
          public List<PlannedFileChange> plan(final Path projectRoot, final GuardMode guard) {
            return List.of();
          }

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
            new InitProjectFiles(
                new PomToolConfigurator(),
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()));
    assertThatThrownBy(
            () ->
                conflicting.initialize(
                    dir,
                    new InitSelection(
                        List.of("opencode"),
                        QualityPreset.STANDARD.gates(),
                        null,
                        QualityPreset.STANDARD)))
        .hasMessageContaining("conflicts");
    assertThat(dir.resolve(WARD_TOML)).doesNotExist();
    assertThat(dir.resolve("config")).doesNotExist();
    assertThat(Files.readString(dir.resolve("pom.xml"))).isEqualTo(PLAIN_POM);
  }

  @Test
  void createsDefaultConfiguration(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);

    final InitResult result = service.init(dir);

    assertThat(result.created()).isTrue();
    assertThat(Files.readString(dir.resolve(WARD_TOML)))
        .isEqualTo(
            """
                version = 1

                [quality-gates.tests]
                enabled = true
                command = "mvn test"
                """);
  }

  @Test
  void createsGradleDefaultConfiguration(@TempDir final Path dir) throws Exception {
    Files.writeString(dir.resolve("build.gradle.kts"), "plugins { java }\n");

    final InitResult result = service.init(dir);

    assertThat(result.created()).isTrue();
    assertThat(Files.readString(dir.resolve(WARD_TOML))).contains("command = \"gradle test\"");
  }

  @Test
  void neverOverwritesExistingConfiguration(@TempDir final Path dir) throws Exception {
    withPom(dir, PLAIN_POM);
    Files.writeString(dir.resolve(WARD_TOML), "version = 1 # mine\n");

    final InitResult result = service.init(dir);

    assertThat(result.created()).isFalse();
    assertThat(Files.readString(dir.resolve(WARD_TOML))).isEqualTo("version = 1 # mine\n");
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
                    dir,
                    new InitSelection(List.of("none"), List.of("tests"), null, null),
                    true,
                    finding -> false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("setup failed");
  }
}
