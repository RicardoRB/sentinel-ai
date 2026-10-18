package dev.sentinel.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingExtractionException;
import dev.sentinel.domain.gate.FindingExtractor;
import dev.sentinel.domain.gate.FindingSeverity;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.gate.SeverityThreshold;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class QualityGateRunnerFindingsTest {

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static QualityGate gate(final String name, final GateStatus status) {
    return new QualityGate() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public GateResult execute(final Project ignored) {
        return new GateResult(name, status, List.of("x"), 1, Duration.ZERO, "out", "err");
      }
    };
  }

  private static Finding finding(final String gate, final int line) {
    return new Finding(
        gate,
        "checkstyle",
        "LineLength",
        FindingSeverity.ERROR,
        "too long",
        "A.java",
        line,
        1,
        null);
  }

  @Test
  void attachesCappedFindingsToFailedGates() {
    final List<Instant> starts = new ArrayList<>();
    final Instant before = Instant.now();
    final FindingExtractor extractor =
        (ignored, result, gateStart) -> {
          starts.add(gateStart);
          return IntStream.rangeClosed(1, 120).mapToObj(i -> finding(result.name(), i)).toList();
        };

    final CheckReport report =
        new QualityGateRunner(extractor)
            .run(project, List.of(gate("checkstyle", GateStatus.FAILED)));

    final GateResult result = report.results().getFirst();
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.findings().items()).hasSize(120);
    assertThat(result.findings().total()).isEqualTo(120);
    assertThat(result.findings().visible(SeverityThreshold.ERROR).truncated()).isTrue();
    assertThat(starts)
        .singleElement()
        .satisfies(start -> assertThat(start).isAfterOrEqualTo(before));
  }

  @Test
  void doesNotExtractForPassedSkippedOrUnavailableGates() {
    final List<String> asked = new ArrayList<>();
    final FindingExtractor extractor =
        (ignored, result, gateStart) -> {
          asked.add(result.name());
          return List.of(finding(result.name(), 1));
        };

    final CheckReport report =
        new QualityGateRunner(extractor)
            .run(
                project,
                List.of(
                    gate("a", GateStatus.PASSED),
                    gate("b", GateStatus.SKIPPED),
                    gate("c", GateStatus.UNAVAILABLE),
                    gate("d", GateStatus.EXECUTION_ERROR)));

    assertThat(asked).isEmpty();
    assertThat(report.results())
        .allSatisfy(result -> assertThat(result.findings().isEmpty()).isTrue());
  }

  @Test
  void extractorFailureKeepsVerdictAndLeavesNoFindings() {
    final FindingExtractor broken =
        (ignored, result, gateStart) -> {
          throw new FindingExtractionException("malformed report", null);
        };

    final CheckReport report =
        new QualityGateRunner(broken)
            .run(project, List.of(gate("pmd", GateStatus.FAILED), gate("b", GateStatus.PASSED)));

    assertThat(report.status()).isEqualTo(GateStatus.FAILED);
    assertThat(report.results())
        .extracting(GateResult::status)
        .containsExactly(GateStatus.FAILED, GateStatus.PASSED);
    assertThat(report.results().getFirst().findings().isEmpty()).isTrue();
    assertThat(report.results().getFirst().stdout()).isEqualTo("out");
  }

  @Test
  void defaultRunnerAttachesNoFindings() {
    final CheckReport report =
        new QualityGateRunner().run(project, List.of(gate("checkstyle", GateStatus.FAILED)));

    assertThat(report.results().getFirst().findings().isEmpty()).isTrue();
  }
}
