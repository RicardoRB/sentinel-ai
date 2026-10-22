package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Enter accepts the shown defaults, and keeping a configuration prints one message. */
class InitDefaultsTest {
  private static final String KEPT = "Kept existing ward.toml. No changes were made.";

  @TempDir Path root;

  @Test
  void emptyPresetAnswerSelectsStandard() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("\n1\n\n\n\n")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("Choose a preset [1-3, Enter=1]");
    assertThat(init.configuration()).contains("preset = \"standard\"");
  }

  @Test
  void emptyArchitectureAnswerSelectsLayered() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("3\n1\n9\n\n\n\n")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("Enter=layered]", "Generated layered ArchUnit test");
  }

  @Test
  void decliningTheOverwritePrintsOneMessage() throws IOException {
    Files.writeString(root.resolve("ward.toml"), "version = 1\n");
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run(true, "n\n")).isEqualTo(ExitCodes.OK);

    assertThat(init.output().split(KEPT, -1)).hasSize(2);
    assertThat(init.output()).doesNotContain("Keeping existing", "Nothing was changed");
    assertThat(init.configuration()).isEqualTo("version = 1\n");
  }
}
