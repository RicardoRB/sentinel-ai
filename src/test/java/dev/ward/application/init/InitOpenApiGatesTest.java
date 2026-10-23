package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.PartialGateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
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
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init offers the OpenAPI gates only for a committed spec and writes their options. */
class InitOpenApiGatesTest {
  private static final String SPEC = "src/main/resources/openapi.yml";
  private static final String LINT = "openapi-lint";
  private static final String DIFF = "openapi-diff";
  private static final String COMPAT = "openapi-compat";

  @TempDir Path dir;

  private Project project() {
    return new Project(dir, Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT, true);
  }

  private static EnvironmentInspection environment(final String spec) {
    return new EnvironmentInspection() {
      @Override
      public EnvironmentFacts inspect(final Path projectRoot) {
        return new EnvironmentFacts(Set.of("vacuum", "oasdiff"), Map.of());
      }

      @Override
      public Optional<String> openApiSpec(final Path projectRoot) {
        return Optional.ofNullable(spec);
      }
    };
  }

  @Test
  void projectWithCommittedSpecIsOfferedTheThreeGates() {
    final InitSetupCatalog catalog = new InitSetupCatalog(environment(SPEC));

    assertThat(catalog.gates(project()))
        .extracting(InitGateOption::id)
        .contains(LINT, DIFF, COMPAT);
    assertThat(catalog.gate(project(), LINT).command())
        .containsExactly("vacuum", "lint", "-z", "-n", "warn");
    assertThat(catalog.gate(project(), LINT).settings()).containsEntry("spec", SPEC);
    assertThat(catalog.gate(project(), COMPAT).settings())
        .containsExactly(Map.entry("spec", SPEC), Map.entry("base", "origin/main"));
    assertThat(catalog.gate(project(), DIFF).available()).isTrue();
  }

  @Test
  void projectWithoutSpecIsNotOfferedTheGates() {
    final InitSetupCatalog catalog = new InitSetupCatalog(environment(null));

    assertThat(catalog.gates(project()))
        .extracting(InitGateOption::id)
        .doesNotContain(LINT, DIFF, COMPAT);
    assertThatThrownBy(() -> catalog.gate(project(), COMPAT))
        .hasMessageContaining("committed OpenAPI spec");
  }

  @Test
  void presetsDoNotSelectOpenApiGates() {
    for (final QualityPreset preset : QualityPreset.values()) {
      assertThat(preset.gates()).doesNotContain(LINT, DIFF, COMPAT);
    }
  }

  @Test
  void generatedConfigurationHasSpecBaseProfilesAndPartialTables() throws IOException {
    final InitSetupCatalog catalog = new InitSetupCatalog(environment(SPEC));
    final List<InitGateOption> gates =
        List.of(
            catalog.gate(project(), LINT),
            catalog.gate(project(), DIFF),
            catalog.gate(project(), COMPAT));
    final Path file = dir.resolve("ward.toml");
    Files.writeString(file, ConfigurationTemplate.render(gates, null, project()));

    final WardConfiguration configuration = new TomlConfigurationReader().read(file);
    final GateConfiguration lint = configuration.gates().get(LINT);
    final GateConfiguration diff = configuration.gates().get(DIFF);
    final GateConfiguration compat = configuration.gates().get(COMPAT);

    assertThat(lint.profiles()).containsExactly("default", "pre-commit", "pre-push", "ci");
    assertThat(diff.profiles()).containsExactly("default", "pre-commit");
    assertThat(compat.profiles()).containsExactly("pre-push", "ci");
    assertThat(lint.openApi()).isEqualTo(new OpenApiGateOptions(SPEC, null));
    assertThat(compat.openApi()).isEqualTo(new OpenApiGateOptions(SPEC, "origin/main"));
    final PartialGateConfiguration specOnly =
        new PartialGateConfiguration(List.of(SPEC), List.of(), List.of(), null, null);
    assertThat(lint.partial()).isEqualTo(specOnly);
    assertThat(diff.partial()).isEqualTo(specOnly);
    assertThat(compat.partial()).isNull();
  }
}
