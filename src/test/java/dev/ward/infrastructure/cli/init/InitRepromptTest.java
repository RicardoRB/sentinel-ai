package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A typo on a terminal asks again; the same typo in piped input fails without changes. */
class InitRepromptTest {
  @TempDir Path root;

  @Test
  void terminalAsksForThePresetAgainAfterATypo() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "4\n1\n1\n\n\nn\n\n");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Invalid preset selection. Choose 1, 2, or 3.");
    assertThat(init.configuration()).contains("preset = \"standard\"");
  }

  @Test
  void terminalAsksForGatesAgainAfterAnOutOfRangeNumber() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "3\n1\n99\n1\n\nn\n\n");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Invalid quality-gate selection '99'");
    assertThat(init.configuration()).contains("[quality-gates.tests]");
  }

  @Test
  void pipedTypoFailsWithoutChanges() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("4\n1\n1\n")).isEqualTo(ExitCodes.ERROR);

    assertThat(init.output()).contains("Invalid preset selection. Choose 1, 2, or 3.");
    assertThat(root.resolve("ward.toml")).doesNotExist();
  }
}
