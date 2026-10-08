package dev.sentinel.domain.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningPolicyTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void countsEpisodesAndPromptsOnResolution() {
    final CheckReport failure = report(GateStatus.FAILED, "x".repeat(250));
    final LearningOutcome first = LearningPolicy.apply(LearningLedger.empty(), failure, 2, NOW);
    final LearningOutcome persistent =
        LearningPolicy.apply(first.ledger(), failure, 2, NOW.plusSeconds(1));
    final LearningOutcome resolved =
        LearningPolicy.apply(
            persistent.ledger(), report(GateStatus.PASSED, "ok"), 2, NOW.plusSeconds(2));
    final LearningOutcome secondFailure =
        LearningPolicy.apply(resolved.ledger(), failure, 2, NOW.plusSeconds(3));
    final LearningOutcome secondResolved =
        LearningPolicy.apply(
            secondFailure.ledger(), report(GateStatus.PASSED, "ok"), 2, NOW.plusSeconds(4));

    assertThat(first.ledger().records().get("checkstyle:FAILED").occurrences()).isOne();
    assertThat(persistent.ledger().records().get("checkstyle:FAILED").occurrences()).isOne();
    assertThat(resolved.prompts()).isEmpty();
    assertThat(secondResolved.prompts())
        .singleElement()
        .satisfies(
            prompt -> {
              assertThat(prompt.occurrences()).isEqualTo(2);
              assertThat(prompt.instruction()).contains("AGENTS.md", "checkstyle");
            });
    assertThat(secondFailure.ledger().records().get("checkstyle:FAILED").summary()).hasSize(200);
  }

  @Test
  void skipsDoNotTouchRecordsAndPromptIsNotDuplicated() {
    final LearningOutcome failed =
        LearningPolicy.apply(LearningLedger.empty(), report(GateStatus.FAILED, "bad"), 1, NOW);
    final LearningOutcome skipped =
        LearningPolicy.apply(
            failed.ledger(), report(GateStatus.SKIPPED, "not run"), 1, NOW.plusSeconds(1));
    final LearningOutcome resolved =
        LearningPolicy.apply(
            skipped.ledger(), report(GateStatus.PASSED, "ok"), 1, NOW.plusSeconds(2));
    final LearningOutcome recurring =
        LearningPolicy.apply(
            resolved.ledger(), report(GateStatus.FAILED, "bad"), 1, NOW.plusSeconds(3));
    final LearningOutcome resolvedAgain =
        LearningPolicy.apply(
            recurring.ledger(), report(GateStatus.PASSED, "ok"), 1, NOW.plusSeconds(4));

    assertThat(skipped.ledger()).isEqualTo(failed.ledger());
    assertThat(resolved.prompts()).hasSize(1);
    assertThat(resolvedAgain.prompts()).isEmpty();
  }

  @Test
  void noPromptWhileFailingEvenAfterThresholdIsReached() {
    final LearningOutcome failure = LearningPolicy.apply(LearningLedger.empty(), failure(), 1, NOW);
    final LearningOutcome persistent =
        LearningPolicy.apply(failure.ledger(), failure(), 1, NOW.plusSeconds(1));

    assertThat(persistent.prompts()).isEmpty();
    assertThat(persistent.ledger().records().get("checkstyle:FAILED").occurrences()).isOne();
    assertThat(persistent.ledger().records().get("checkstyle:FAILED").state())
        .isEqualTo(LearningState.FAILING);
    assertThat(persistent.ledger().records().get("checkstyle:FAILED").prompted()).isFalse();
  }

  @Test
  void belowThresholdResolutionEmitsNoPromptAndMarksResolved() {
    final LearningOutcome failure = LearningPolicy.apply(LearningLedger.empty(), failure(), 3, NOW);
    final LearningOutcome resolved =
        LearningPolicy.apply(
            failure.ledger(), report(GateStatus.PASSED, "ok"), 3, NOW.plusSeconds(1));

    assertThat(resolved.prompts()).isEmpty();
    final LearningRecord record = resolved.ledger().records().get("checkstyle:FAILED");
    assertThat(record.occurrences()).isOne();
    assertThat(record.state()).isEqualTo(LearningState.RESOLVED);
    assertThat(record.prompted()).isFalse();
  }

  @Test
  void recordsStoreOnlyTheShortSummaryNeverStdoutOrStderr() {
    final LearningOutcome failure = LearningPolicy.apply(LearningLedger.empty(), failure(), 1, NOW);

    final LearningRecord record = failure.ledger().records().get("checkstyle:FAILED");
    assertThat(record.summary()).isEqualTo("Build broke");
    assertThat(record.summary()).doesNotContain("stdout must not be stored");
    assertThat(record.summary()).doesNotContain("stderr must not be stored");
    assertThat(record).hasNoNullFieldsOrProperties();
    assertThat(record.summary()).hasSizeLessThanOrEqualTo(200);
  }

  @Test
  void failsToFixThenFailsAgainCountsTwoOccurrences() {
    final LearningOutcome failure = LearningPolicy.apply(LearningLedger.empty(), failure(), 1, NOW);
    final LearningOutcome resolved =
        LearningPolicy.apply(
            failure.ledger(), report(GateStatus.PASSED, "ok"), 1, NOW.plusSeconds(1));
    final LearningOutcome secondFailure =
        LearningPolicy.apply(resolved.ledger(), failure(), 1, NOW.plusSeconds(2));

    final LearningRecord record = secondFailure.ledger().records().get("checkstyle:FAILED");
    assertThat(record.occurrences()).isEqualTo(2);
    assertThat(record.state()).isEqualTo(LearningState.FAILING);
    assertThat(record.firstSeen()).isEqualTo(NOW);
  }

  private static CheckReport failure() {
    return new CheckReport(
        PROJECT,
        List.of(
            new GateResult(
                "checkstyle",
                GateStatus.FAILED,
                List.of("checkstyle"),
                1,
                Duration.ZERO,
                "stdout must not be stored",
                "stderr must not be stored",
                "Build broke")));
  }

  private static CheckReport report(final GateStatus status, final String summary) {
    return new CheckReport(
        PROJECT,
        List.of(
            new GateResult(
                "checkstyle",
                status,
                List.of("checkstyle"),
                status == GateStatus.PASSED ? 0 : 1,
                Duration.ZERO,
                "stdout must not be stored",
                "stderr must not be stored",
                summary)));
  }
}
