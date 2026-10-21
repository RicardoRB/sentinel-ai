package dev.sentinel.domain.learning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.GateFindings;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which findings become rule records, and what the records and prompts carry. */
class LearningPolicyFindingsTest extends LearningPolicyTestSupport {
  private static final String CHECKSTYLE = "checkstyle";
  private static final int VIOLATIONS = 12;

  @Test
  void promptNamesTheRuleAndForbidsCommandRules() {
    final LearningOutcome failure =
        LearningPolicy.apply(LearningLedger.empty(), unusedVariable(), 1, NOW);

    final LearningOutcome resolved = apply(failure, passed(PMD), 1, 1);

    assertThat(resolved.prompts())
        .singleElement()
        .extracting(LearningPrompt::instruction)
        .asString()
        .contains("AGENTS.md", "`pmd`", "`UnusedLocalVariable`", UNUSED_MESSAGE)
        .contains("do not write a rule about running the gate, Sentinel, or any command");
  }

  @Test
  void manyViolationsOfOneRuleMakeOneRecordWithTheFirstMessage() {
    final List<Finding> findings =
        Collections.nCopies(VIOLATIONS, finding(CHECKSTYLE, "FinalParameters", "first"));

    final LearningOutcome outcome =
        LearningPolicy.apply(
            LearningLedger.empty(), failed(CHECKSTYLE, findings.toArray(Finding[]::new)), 1, NOW);

    assertThat(outcome.ledger().records().values())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.occurrences()).isOne();
              assertThat(record.summary()).isEqualTo("first");
            });
  }

  @Test
  void differentRulesAndGatesGetSeparateRecords() {
    final CheckReport report =
        new CheckReport(
            PROJECT,
            List.of(
                failedResult(CHECKSTYLE, finding(CHECKSTYLE, "FinalParameters", "final")),
                failedResult(
                    PMD, finding(PMD, UNUSED, UNUSED_MESSAGE), finding(PMD, PRIVATE, "private")),
                failedResult("gitleaks", finding("gitleaks", "generic-api-key", "secret"))));

    final LearningOutcome outcome = LearningPolicy.apply(LearningLedger.empty(), report, 1, NOW);

    assertThat(outcome.ledger().records())
        .containsOnlyKeys(
            "checkstyle:FinalParameters", UNUSED_KEY, PRIVATE_KEY, "gitleaks:generic-api-key");
    assertThat(outcome.ledger().records().get(UNUSED_KEY).rule()).isEqualTo(UNUSED);
  }

  @Test
  void unlearnableFailuresCreateNoRecords() {
    final CheckReport report =
        new CheckReport(
            PROJECT,
            List.of(
                failedResult("test"),
                failedResult("format", finding("spotless", "format", "not formatted")),
                failedResult("compile", finding("javac", "javac.error", "cannot find symbol")),
                failedResult(PMD, finding(PMD, PMD, "blank rule")),
                GateResult.builder(CHECKSTYLE, GateStatus.PASSED)
                    .build()
                    .withFindings(
                        GateFindings.of(List.of(finding(CHECKSTYLE, "MagicNumber", "warn"))))));

    final LearningOutcome outcome = LearningPolicy.apply(LearningLedger.empty(), report, 1, NOW);

    assertThat(outcome.ledger().records()).isEmpty();
  }

  @Test
  void recordsStoreOnlyTheFindingMessageNeverStdoutOrStderr() {
    final GateResult result =
        GateResult.builder(PMD, GateStatus.FAILED)
            .stdout("stdout must not be stored")
            .stderr("stderr must not be stored")
            .summary("Build broke")
            .build()
            .withFindings(GateFindings.of(List.of(finding(PMD, UNUSED, UNUSED_MESSAGE))));

    final LearningRecord record =
        LearningPolicy.apply(
                LearningLedger.empty(), new CheckReport(PROJECT, List.of(result)), 1, NOW)
            .ledger()
            .records()
            .get(UNUSED_KEY);

    assertThat(record.summary()).isEqualTo(UNUSED_MESSAGE);
    assertThat(record).hasNoNullFieldsOrProperties();
  }
}
