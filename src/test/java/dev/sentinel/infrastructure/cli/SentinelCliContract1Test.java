package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.Command;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliContract1Test extends SentinelCliIntegrationSupport {
  @Test
  void factoryResolvesEverySentinelSubcommand() throws Exception {
    final Class<?>[] subcommands = SentinelCommand.class.getAnnotation(Command.class).subcommands();
    assertThat(subcommands).isNotEmpty();
    for (final Class<?> subcommand : subcommands) {
      assertThat(factory.create(subcommand)).isInstanceOf(subcommand);
    }
  }

  @Test
  void detectsFixtureProject() {
    assertThat(run("detect", "-C", project.toString())).isZero();
    assertThat(out.toString())
        .contains("Project detected", "Java", "Maven", "Spring Boot", project.toString());
  }

  @Test
  void detectOnUnsupportedDirectoryExitsNonZero(@TempDir final Path empty) {
    assertThat(run("detect", "-C", empty.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString().trim()).isEqualTo("No supported project detected.");
  }

  @Test
  void initThenCheckPasses() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString()))
        .isZero();
    assertThat(project.resolve(SENTINEL_TOML)).exists();

    out.getBuffer().setLength(0);
    assertThat(run(CHECK, "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(out.toString())
        .contains("Sentinel dev", "✓ tests", "PASSED", "Quality Gate: PASSED");
  }

  @Test
  void presetStandardWritesConfigurationAndRuleFiles() throws IOException {
    assertThat(
            run(INIT, "--preset", "standard", INTEGRATION_OPTION, NONE, "-C", project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve(SENTINEL_TOML)))
        .contains("preset = \"standard\"", "quality-gates.pmd");
    assertThat(project.resolve("config/pmd-ruleset.xml")).exists();
  }

  @Test
  void unknownPresetChangesNothing() throws IOException {
    assertThat(
            run(INIT, "--preset", "paranoid", INTEGRATION_OPTION, NONE, "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(project.resolve(SENTINEL_TOML)).doesNotExist();
    assertThat(project.resolve("config")).doesNotExist();
  }

  @Test
  void strictPresetGeneratesArchitectureAndEnforcerFiles() throws IOException {
    assertThat(
            run(
                INIT,
                "--preset",
                "strict",
                INTEGRATION_OPTION,
                NONE,
                "--architecture",
                "layered",
                "-C",
                project.toString()))
        .isZero();
    assertThat(project.resolve("config/enforcer-rules.xml")).exists();
    assertThat(Files.readString(project.resolve(SENTINEL_TOML))).contains("quality-gates.mutation");
    try (Stream<Path> files = Files.walk(project.resolve("src/test/java"))) {
      assertThat(files)
          .anyMatch(path -> "ArchitectureTest.java".equals(path.getFileName().toString()));
    }
  }

  @Test
  void presetCanAddExplicitGate() throws IOException {
    assertThat(
            run(
                INIT,
                "--preset",
                "standard",
                GATE_OPTION,
                "gitleaks",
                INTEGRATION_OPTION,
                NONE,
                "-C",
                project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve(SENTINEL_TOML))).contains("quality-gates.gitleaks");
  }
}
