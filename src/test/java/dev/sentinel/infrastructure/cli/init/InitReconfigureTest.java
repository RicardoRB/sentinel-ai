package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Overwriting an existing configuration starts from its recorded preset and gates. */
class InitReconfigureTest {
  private static final String MUTATION_ROW = "10";

  @TempDir Path root;

  @Test
  void changeOneGateOfAStrictConfiguration() throws IOException {
    final InitCommandFixture create = new InitCommandFixture(root);
    assertThat(create.run("", "--yes", "--preset", "strict", "--integration", "none"))
        .isEqualTo(ExitCodes.OK);
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "y\n\n1\n" + MUTATION_ROW + "\n\n\nn\n\n");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Choose a preset [1-3, Enter=2]");
    assertThat(init.configuration())
        .contains("preset = \"strict\"", "[quality-gates.archunit]", "[quality-gates.enforcer]")
        .doesNotContain("[quality-gates.mutation]");
  }

  @Test
  void customConfigurationKeepsItsGates() throws IOException {
    final InitCommandFixture create = new InitCommandFixture(root);
    assertThat(create.run("", "--integration", "none", "--gate", "tests,gitleaks"))
        .isEqualTo(ExitCodes.OK);
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "y\n\n1\n\n\nn\n\n");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Choose a preset [1-3, Enter=3]");
    assertThat(init.configuration())
        .contains("[quality-gates.tests]", "[quality-gates.gitleaks]")
        .doesNotContain("preset =");
  }

  @Test
  void unreadableConfigurationUsesTheUsualDefaults() throws IOException {
    Files.writeString(root.resolve("sentinel.toml"), "not [valid toml");
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "y\n\n1\n\n\nn\n\n");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Choose a preset [1-3, Enter=1]");
    assertThat(init.configuration()).contains("preset = \"standard\"");
  }
}
