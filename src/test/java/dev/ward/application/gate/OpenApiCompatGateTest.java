package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class OpenApiCompatGateTest {
  private static final String COMPAT = "openapi-compat";
  private static final String ORIGIN_MAIN = "origin/main";

  private static OpenApiGateFixtures branch() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.revisions.put(ORIGIN_MAIN, OpenApiGateFixtures.BASE_SHA);
    fixtures.mergeBase = Optional.of(OpenApiGateFixtures.BASE_SHA);
    fixtures.committedFiles.add(OpenApiGateFixtures.BASE_SHA + ":" + OpenApiGateFixtures.SPEC);
    return fixtures;
  }

  @Test
  void comparesWithMergeBaseOfOriginMain() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        branch()
            .gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands.getFirst())
        .endsWith(
            OpenApiGateFixtures.BASE_SHA + ":./" + OpenApiGateFixtures.SPEC,
            OpenApiGateFixtures.SPEC);
  }

  @Test
  void branchThatBreaksTheApiFails() {
    final GateResult result =
        branch()
            .gate(
                COMPAT,
                OpenApiGateFixtures.DIFF_COMMAND,
                new FakeCommandExecutor(1, "1 changes: 1 error", ""))
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void onTheBaseBranchComparesWithFirstParent() {
    final OpenApiGateFixtures fixtures = branch();
    fixtures.mergeBase = Optional.of(OpenApiGateFixtures.HEAD_SHA);
    fixtures.revisions.put(OpenApiGateFixtures.HEAD_SHA + "^1", OpenApiGateFixtures.PARENT_SHA);
    fixtures.committedFiles.add(OpenApiGateFixtures.PARENT_SHA + ":" + OpenApiGateFixtures.SPEC);
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    fixtures
        .gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND, executor)
        .execute(OpenApiGateFixtures.PROJECT);

    assertThat(executor.commands.getFirst())
        .endsWith(
            OpenApiGateFixtures.PARENT_SHA + ":./" + OpenApiGateFixtures.SPEC,
            OpenApiGateFixtures.SPEC);
  }

  @Test
  void firstCommitOnTheBaseBranchPassesWithoutEarlierRevision() {
    final OpenApiGateFixtures fixtures = branch();
    fixtures.mergeBase = Optional.of(OpenApiGateFixtures.HEAD_SHA);

    final GateResult result =
        fixtures
            .gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("No earlier revision");
  }

  @Test
  void specNewOnTheBranchPassesWithoutBaseline() {
    final OpenApiGateFixtures fixtures = branch();
    fixtures.committedFiles.remove(OpenApiGateFixtures.BASE_SHA + ":" + OpenApiGateFixtures.SPEC);

    final GateResult result =
        fixtures
            .gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.summary()).contains("No baseline").contains(OpenApiGateFixtures.BASE_SHA);
  }

  @Test
  void unknownBaseRefIsAConfigurationError() {
    final OpenApiGateFixtures fixtures = branch();
    fixtures.revisions.remove(ORIGIN_MAIN);

    assertThatThrownBy(() -> fixtures.gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND))
        .isInstanceOf(WardException.class)
        .hasMessageContaining(ORIGIN_MAIN);
  }

  @Test
  void shallowCloneWithoutMergeBaseFailsWithFetchHint() {
    final OpenApiGateFixtures fixtures = branch();
    fixtures.mergeBase = Optional.empty();

    final GateResult result =
        fixtures
            .gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("fetch the base branch history");
  }

  @Test
  void logsResolvedRevisionAndNoBaselineReasonsWithoutSpecContents() {
    final List<LogRecord> records = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            records.add(logRecord);
          }

          @Override
          public void flush() {
            // Records are kept in memory.
          }

          @Override
          public void close() {
            // Nothing to release.
          }
        };
    final Logger logger = Logger.getLogger(OpenApiDiffGate.class.getName());
    logger.addHandler(handler);
    try {
      branch().gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND).execute(OpenApiGateFixtures.PROJECT);
      final OpenApiGateFixtures newSpec = branch();
      newSpec.committedFiles.remove(OpenApiGateFixtures.BASE_SHA + ":" + OpenApiGateFixtures.SPEC);
      newSpec.gate(COMPAT, OpenApiGateFixtures.DIFF_COMMAND).execute(OpenApiGateFixtures.PROJECT);
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(records)
        .extracting(LogRecord::getMessage)
        .containsExactly(
            "event=openapi-baseline gate=openapi-compat status=found revision="
                + OpenApiGateFixtures.BASE_SHA,
            "event=openapi-baseline gate=openapi-compat status=none reason=new-spec revision="
                + OpenApiGateFixtures.BASE_SHA)
        .noneMatch(message -> message.contains("openapi: 3.0.3"));
    assertThat(records).extracting(LogRecord::getLevel).containsOnly(Level.INFO);
  }
}
