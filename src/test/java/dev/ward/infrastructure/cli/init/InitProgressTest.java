package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every wizard question is introduced with Step N of M, and follow-ups extend the total. */
class InitProgressTest {
  @TempDir Path root;

  @Test
  void questionsWithoutOptionsCountToFive() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("3\n1\n1\n\n\n")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output())
        .contains("Step 1 of 5", "Step 2 of 5", "Step 3 of 5", "Step 4 of 5", "Step 5 of 5")
        .doesNotContain("Step 6");
  }

  @Test
  void archunitAddsTheArchitectureStep() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run("3\n1\n9\n1\n\n\n")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output()).contains("Step 3 of 5", "Step 4 of 6", "Step 5 of 6", "Step 6 of 6");
  }

  @Test
  void integrationOptionLeavesFourStepsOnATerminal() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit = init.run(true, "1\n\n\nn\n\n", "--integration", "none");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("Step 1 of 4", "Step 4 of 4").doesNotContain("Step 5");
  }

  @Test
  void reaskingAfterATypoKeepsTheSameStep() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    assertThat(init.run(true, "4\n3\n1\n1\n\nn\n\n")).as(init.output()).isEqualTo(ExitCodes.OK);

    assertThat(init.output().split("Step 1 of 5", -1)).hasSize(2);
  }
}
