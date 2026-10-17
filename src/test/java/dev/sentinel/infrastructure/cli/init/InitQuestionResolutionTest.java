package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Options answer questions, --yes supplies defaults, and only the gate selection is required. */
class InitQuestionResolutionTest {
  @TempDir Path root;

  @Test
  void yesAloneConfiguresStandardDefaultsWithoutQuestions() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("", "--yes")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output())
        .doesNotContain(
            "Choose a preset", "Select agent", "Select quality gates", "Select optional", "[y/N]");
    for (final String gate : QualityPreset.STANDARD.gates()) {
      assertThat(init.configuration()).contains("[quality-gates." + gate + "]");
    }
    assertThat(init.output()).contains("No agent integration installed.");
    assertThat(root.resolve(".git/hooks/pre-commit")).doesNotExist();
  }

  @Test
  void optionsWinOverYesDefaults() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("", "--yes", "--preset", "strict", "--integration", "claude-code");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.configuration()).contains("preset = \"strict\"", "[quality-gates.archunit]");
    assertThat(init.output()).contains("Generated layered ArchUnit test");
    assertThat(root.resolve(".claude/settings.json")).exists();
  }

  @Test
  void yesKeepsAnExistingConfiguration() throws IOException {
    Files.writeString(root.resolve("sentinel.toml"), "version = 1\n");
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("", "--yes")).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("already exists. Nothing was changed.");
    assertThat(init.configuration()).isEqualTo("version = 1\n");
  }

  @Test
  void terminalStillAsksWhatAnIntegrationOptionLeavesOpen() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "1\n\n\nn\ny\n", "--integration", "claude-code");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Choose a preset", "Select quality gates", "Select optional tools", "[y/N]")
        .doesNotContain("Select agent integrations");
  }

  @Test
  void automationWithOptionsIsNeverAsked() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run("1\n", "--integration", "none", "--gate", "tests");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).doesNotContain("Select optional tools", "[y/N]");
    assertThat(init.configuration()).contains("[quality-gates.tests]");
  }

  @Test
  void automationWithoutGateSelectionFailsFast() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("1\n", "--integration", "none")).isEqualTo(ExitCodes.ERROR);

    assertThat(init.output())
        .contains("No gate selection given", "--preset", "--gate", "--yes")
        .doesNotContain("Choose a preset");
    assertThat(root.resolve("sentinel.toml")).doesNotExist();
  }

  @Test
  void emptyInputWithoutOptionsNamesTheMissingOptions() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("")).isEqualTo(ExitCodes.ERROR);

    assertThat(init.output())
        .contains("Input ended before the gate selection", "--preset", "--gate", "--yes");
    assertThat(root.resolve("sentinel.toml")).doesNotExist();
  }

  @Test
  void gateOptionAloneUsesDefaultsForEverythingElse() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("", "--gate", "tests")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("No agent integration installed.");
    assertThat(init.configuration()).contains("[quality-gates.tests]");
  }

  @Test
  void endOfInputAtIntegrationsSelectsNoneThenStopsAtGates() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("1\n")).isEqualTo(ExitCodes.ERROR);

    assertThat(init.output()).contains("Input ended before the gate selection");
    assertThat(root.resolve("sentinel.toml")).doesNotExist();
  }
}
