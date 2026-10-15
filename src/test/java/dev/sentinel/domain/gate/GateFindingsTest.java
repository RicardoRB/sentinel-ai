package dev.sentinel.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GateFindingsTest {

  private static Finding finding(final int line) {
    return new Finding(
        "checkstyle",
        "checkstyle",
        "LineLength",
        FindingSeverity.ERROR,
        "Line is longer than 100 characters.",
        "src/main/java/Foo.java",
        line,
        null,
        null);
  }

  @Test
  void keepsAtMostFiftyFindingsInReportOrderAndRecordsTotal() {
    final List<Finding> all =
        IntStream.rangeClosed(1, 120).mapToObj(GateFindingsTest::finding).toList();

    final GateFindings findings = GateFindings.of(all);

    assertThat(findings.items()).hasSize(GateFindings.LIMIT);
    assertThat(findings.items().getFirst().line()).isEqualTo(1);
    assertThat(findings.items().getLast().line()).isEqualTo(50);
    assertThat(findings.total()).isEqualTo(120);
    assertThat(findings.truncated()).isTrue();
    assertThat(findings.isEmpty()).isFalse();
  }

  @Test
  void smallListsAreNotTruncated() {
    final GateFindings findings = GateFindings.of(List.of(finding(3)));

    assertThat(findings.items()).hasSize(1);
    assertThat(findings.total()).isEqualTo(1);
    assertThat(findings.truncated()).isFalse();
  }

  @Test
  void emptyListIsNone() {
    assertThat(GateFindings.of(List.of())).isSameAs(GateFindings.NONE);
    assertThat(GateFindings.NONE.isEmpty()).isTrue();
    assertThat(GateFindings.NONE.truncated()).isFalse();
  }

  @Test
  void totalCannotBeSmallerThanKeptItems() {
    assertThatThrownBy(() -> new GateFindings(List.of(finding(1)), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fileLevelFindingHasNoLineOrColumn() {
    final Finding finding =
        new Finding(
            "format",
            "spotless",
            "format",
            FindingSeverity.ERROR,
            "File is not formatted.",
            "src/main/java/Foo.java",
            null,
            null,
            null);

    assertThat(finding.located()).isTrue();
    assertThat(finding.line()).isNull();
    assertThat(finding.column()).isNull();
    assertThat(finding.severity().label()).isEqualTo("error");
  }

  @Test
  void locationIsOneBased() {
    assertThatThrownBy(
            () ->
                new Finding(
                    "pmd", "pmd", "X", FindingSeverity.NOTE, "m", "Foo.java", 0, null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Finding("pmd", "pmd", "X", FindingSeverity.NOTE, "m", "Foo.java", 1, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new Finding(
                    "tests", "tests", "tests", FindingSeverity.WARNING, "m", null, null, null, null)
                .located())
        .isFalse();
  }
}
