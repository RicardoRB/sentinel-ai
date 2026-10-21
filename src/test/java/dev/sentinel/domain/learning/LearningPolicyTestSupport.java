package dev.sentinel.domain.learning;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingSeverity;
import dev.sentinel.domain.gate.GateFindings;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Builds check reports with rule findings for the learning policy tests. */
class LearningPolicyTestSupport {
  static final String PMD = "pmd";
  static final String UNUSED = "UnusedLocalVariable";
  static final String UNUSED_KEY = "pmd:UnusedLocalVariable";
  static final String PRIVATE = "UnusedPrivateMethod";
  static final String PRIVATE_KEY = "pmd:UnusedPrivateMethod";
  static final String UNUSED_MESSAGE = "Avoid unused local variables such as 'x'.";
  static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  LearningOutcome apply(
      final LearningOutcome previous,
      final CheckReport report,
      final int threshold,
      final long seconds) {
    return LearningPolicy.apply(previous.ledger(), report, threshold, NOW.plusSeconds(seconds));
  }

  Finding finding(final String tool, final String rule, final String message) {
    return new Finding(
        tool, tool, rule, FindingSeverity.ERROR, message, "src/Main.java", 1, null, null);
  }

  GateResult failedResult(final String gate, final Finding... findings) {
    return GateResult.builder(gate, GateStatus.FAILED)
        .exitCode(1)
        .build()
        .withFindings(GateFindings.of(List.of(findings)));
  }

  CheckReport failed(final String gate, final Finding... findings) {
    return new CheckReport(PROJECT, List.of(failedResult(gate, findings)));
  }

  CheckReport unusedVariable() {
    return failed(PMD, finding(PMD, UNUSED, UNUSED_MESSAGE));
  }

  CheckReport passed(final String gate) {
    return new CheckReport(PROJECT, List.of(GateResult.builder(gate, GateStatus.PASSED).build()));
  }
}
