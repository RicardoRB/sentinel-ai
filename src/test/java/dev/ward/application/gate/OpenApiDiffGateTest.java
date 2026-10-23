package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenApiDiffGateTest {
  private static final String DIFF = "openapi-diff";
  private static final int BREAKING = 1;

  @Test
  void comparesWorkingTreeSpecWithHead() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        OpenApiGateFixtures.committedSpec()
            .gate(DIFF, OpenApiGateFixtures.DIFF_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands)
        .containsExactly(
            List.of(
                "oasdiff",
                "breaking",
                "--fail-on",
                "ERR",
                OpenApiGateFixtures.HEAD_SHA + ":./" + OpenApiGateFixtures.SPEC,
                OpenApiGateFixtures.SPEC));
  }

  @Test
  void uncommittedBreakingChangeFailsTheGate() {
    final GateResult result =
        OpenApiGateFixtures.committedSpec()
            .gate(
                DIFF,
                OpenApiGateFixtures.DIFF_COMMAND,
                new FakeCommandExecutor(BREAKING, "1 changes: 1 error", ""))
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void specNotYetCommittedPassesWithoutBaseline() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.committedFiles.clear();
    final FakeCommandExecutor executor = new FakeCommandExecutor(BREAKING, "", "");

    final GateResult result =
        fixtures
            .gate(DIFF, OpenApiGateFixtures.DIFF_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("No baseline").contains("new");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void repositoryWithoutCommitsPassesWithoutBaseline() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.revisions.clear();

    final GateResult result =
        fixtures.gate(DIFF, OpenApiGateFixtures.DIFF_COMMAND).execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("no commits");
  }

  @Test
  void oasdiffConfigurationFileFailsTheGate() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.workingTree.put(".oasdiff.yaml", "err-ignore: ignore.txt\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        fixtures
            .gate(DIFF, OpenApiGateFixtures.DIFF_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains(".oasdiff.yaml").doesNotContain("ignore.txt");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void oasdiffConfigurationVariableFailsWithoutItsValue() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.environment.put("OASDIFF_CONFIG", "true");

    final GateResult result =
        fixtures.gate(DIFF, OpenApiGateFixtures.DIFF_COMMAND).execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("OASDIFF_CONFIG").doesNotContain("true");
  }

  @Test
  void missingSpecFailsTheGate() {
    final GateResult result =
        new OpenApiGateFixtures()
            .gate(DIFF, OpenApiGateFixtures.DIFF_COMMAND)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("does not exist");
  }
}
