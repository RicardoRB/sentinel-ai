package dev.sentinel.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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
  void keepsAllFindingsAndCapsVisibleFindingsAtPresentationTime() {
    final List<Finding> all =
        IntStream.rangeClosed(1, 120).mapToObj(GateFindingsTest::finding).toList();

    final GateFindings findings = GateFindings.of(all);

    assertThat(findings.items()).hasSize(120);
    assertThat(findings.items().getFirst().line()).isEqualTo(1);
    assertThat(findings.items().getLast().line()).isEqualTo(120);
    assertThat(findings.total()).isEqualTo(120);
    assertThat(findings.visible(SeverityThreshold.ERROR).items()).hasSize(50);
    assertThat(findings.visible(SeverityThreshold.ERROR).truncated()).isTrue();
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
  void filtersBeforeApplyingTheCap() {
    final List<Finding> all =
        Stream.concat(
                IntStream.rangeClosed(1, 60)
                    .mapToObj(i -> findingWithSeverity(i, FindingSeverity.WARNING)),
                IntStream.rangeClosed(61, 63)
                    .mapToObj(i -> findingWithSeverity(i, FindingSeverity.ERROR)))
            .toList();

    final GateFindings.Visible visible = GateFindings.of(all).visible(SeverityThreshold.ERROR);

    assertThat(visible.items()).extracting(Finding::severity).containsOnly(FindingSeverity.ERROR);
    assertThat(visible.items()).extracting(Finding::line).containsExactly(61, 62, 63);
    assertThat(visible.total()).isEqualTo(63);
    assertThat(visible.hidden()).isEqualTo(60);
    assertThat(visible.truncated()).isFalse();
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

  private static Finding findingWithSeverity(final int line, final FindingSeverity severity) {
    return new Finding(
        "checkstyle",
        "checkstyle",
        "LineLength",
        severity,
        "message",
        "Foo.java",
        line,
        null,
        null);
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
