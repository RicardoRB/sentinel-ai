package dev.ward.domain.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Recurrence counting, resolution and prompting of rule records. */
class LearningPolicyTest extends LearningPolicyTestSupport {
  @Test
  void countsRecurrencesAndPromptsOnResolution() {
    final CheckReport failure = failed(PMD, finding(PMD, UNUSED, "x".repeat(250)));
    final LearningOutcome first = LearningPolicy.apply(LearningLedger.empty(), failure, 2, NOW);
    final LearningOutcome persistent = apply(first, failure, 2, 1);
    final LearningOutcome resolved = apply(persistent, passed(PMD), 2, 2);
    final LearningOutcome secondFailure = apply(resolved, failure, 2, 3);
    final LearningOutcome secondResolved = apply(secondFailure, passed(PMD), 2, 4);

    assertThat(first.ledger().records().get(UNUSED_KEY).occurrences()).isOne();
    assertThat(persistent.ledger().records().get(UNUSED_KEY).occurrences()).isOne();
    assertThat(resolved.prompts()).isEmpty();
    assertThat(secondFailure.prompts()).isEmpty();
    assertThat(secondResolved.prompts())
        .singleElement()
        .satisfies(
            prompt -> {
              assertThat(prompt.key()).isEqualTo(UNUSED_KEY);
              assertThat(prompt.rule()).isEqualTo(UNUSED);
              assertThat(prompt.occurrences()).isEqualTo(2);
            });
    assertThat(secondFailure.ledger().records().get(UNUSED_KEY).summary())
        .hasSize(LearningRecord.MAX_SUMMARY_LENGTH);
    assertThat(secondFailure.ledger().records().get(UNUSED_KEY).firstSeen()).isEqualTo(NOW);
  }

  @Test
  void ruleFixedWhileItsGateStillFailsResolvesAndCountsItsRecurrence() {
    final LearningOutcome first =
        LearningPolicy.apply(LearningLedger.empty(), unusedVariable(), 2, NOW);
    final LearningOutcome otherRule = apply(first, failed(PMD, finding(PMD, PRIVATE, "p")), 2, 1);
    final LearningOutcome recurred = apply(otherRule, unusedVariable(), 2, 2);

    assertThat(otherRule.ledger().records().get(UNUSED_KEY).state())
        .isEqualTo(LearningState.RESOLVED);
    assertThat(otherRule.ledger().records().get(PRIVATE_KEY).state())
        .isEqualTo(LearningState.FAILING);
    assertThat(recurred.ledger().records().get(UNUSED_KEY).occurrences()).isEqualTo(2);
    assertThat(recurred.ledger().records().get(PRIVATE_KEY).state())
        .isEqualTo(LearningState.RESOLVED);
  }

  @ParameterizedTest
  @EnumSource(
      value = GateStatus.class,
      names = {"SKIPPED", "UNAVAILABLE", "EXECUTION_ERROR"})
  void gatesNotEvaluatedKeepTheirRecords(final GateStatus status) {
    final LearningLedger failing =
        LearningPolicy.apply(LearningLedger.empty(), unusedVariable(), 1, NOW).ledger();
    final CheckReport report =
        new CheckReport(PROJECT, List.of(GateResult.builder(PMD, status).build()));

    final LearningOutcome outcome = LearningPolicy.apply(failing, report, 1, NOW.plusSeconds(1));

    assertThat(outcome.ledger()).isEqualTo(failing);
    assertThat(outcome.prompts()).isEmpty();
  }

  @Test
  void failedGateWithoutRuleFindingsResolvesNothing() {
    final LearningLedger failing =
        LearningPolicy.apply(LearningLedger.empty(), unusedVariable(), 1, NOW).ledger();

    assertThat(LearningPolicy.apply(failing, failed(PMD), 1, NOW.plusSeconds(1)).ledger())
        .isEqualTo(failing);
  }

  @Test
  void noPromptWhileFailingAndNoDuplicatePromptAfterRecurrence() {
    final LearningOutcome first =
        LearningPolicy.apply(LearningLedger.empty(), unusedVariable(), 1, NOW);
    final LearningOutcome persistent = apply(first, unusedVariable(), 1, 1);
    final LearningOutcome resolved = apply(persistent, passed(PMD), 1, 2);
    final LearningOutcome recurring = apply(resolved, unusedVariable(), 1, 3);
    final LearningOutcome resolvedAgain = apply(recurring, passed(PMD), 1, 4);

    assertThat(persistent.prompts()).isEmpty();
    assertThat(persistent.ledger().records().get(UNUSED_KEY).prompted()).isFalse();
    assertThat(resolved.prompts()).hasSize(1);
    assertThat(resolvedAgain.prompts()).isEmpty();
  }

  @Test
  void belowThresholdResolutionEmitsNoPromptAndMarksResolved() {
    final LearningOutcome failure =
        LearningPolicy.apply(LearningLedger.empty(), unusedVariable(), 3, NOW);

    final LearningOutcome resolved = apply(failure, passed(PMD), 3, 1);

    assertThat(resolved.prompts()).isEmpty();
    final LearningRecord record = resolved.ledger().records().get(UNUSED_KEY);
    assertThat(record.occurrences()).isOne();
    assertThat(record.state()).isEqualTo(LearningState.RESOLVED);
    assertThat(record.prompted()).isFalse();
  }
}
