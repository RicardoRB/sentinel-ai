package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.ProjectSnapshot;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The terminal review applies, declines, or is skipped, and conflicts stop before writing. */
class InitReviewTest {
  private static final String[] TESTS_ONLY = {"--integration", "none", "--gate", "tests"};

  @TempDir Path root;

  @Test
  void terminalReviewAppliesOnEnter() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run(true, "\nn\n\n", TESTS_ONLY)).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("Sentinel will make these changes:", "Proceed? [Y/n]");
    assertThat(init.configuration()).contains("[quality-gates.tests]");
  }

  @Test
  void terminalReviewDeclinedChangesNothing() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);
    final Map<String, String> before = ProjectSnapshot.of(root);

    assertThat(init.run(true, "\nn\nn\n", TESTS_ONLY)).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("No changes made.");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void endOfInputAtTheReviewChangesNothing() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run(true, "\nn\n", TESTS_ONLY)).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("No changes made.");
    assertThat(root.resolve("sentinel.toml")).doesNotExist();
  }

  @Test
  void reviewIsSkippedWithYesAndForPipedInput() throws IOException {
    final InitCommandFixture withYes = new InitCommandFixture(root);
    assertThat(withYes.run(true, "", "--yes", "--gate", "tests")).isEqualTo(ExitCodes.OK);
    assertThat(withYes.output()).doesNotContain("Proceed?");

    Files.delete(root.resolve("sentinel.toml"));
    final InitCommandFixture piped = new InitCommandFixture(root);
    assertThat(piped.run("", TESTS_ONLY)).isEqualTo(ExitCodes.OK);
    assertThat(piped.output()).doesNotContain("Proceed?");
  }

  @Test
  void conflictStopsBeforeWriting() throws IOException {
    Files.createDirectories(root.resolve(".claude"));
    Files.writeString(root.resolve(".claude/settings.json"), "{ not json");
    final InitCommandFixture init = new InitCommandFixture(root);
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", "--integration", "claude-code", "--gate", "tests");

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output())
        .contains("conflict   .claude/settings.json", "conflict with user-owned content");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }
}
