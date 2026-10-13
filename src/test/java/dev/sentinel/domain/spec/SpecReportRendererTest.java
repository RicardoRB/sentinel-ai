package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SpecReportRendererTest {

  @Test
  void rendersItemsWithCountsAndSeverityOrderedFindings() {
    final SpecValidationReport report =
        new SpecValidationReport(
            List.of(
                new SpecItemReport(
                    "openspec change foo",
                    OpenSpecOutput.parse(
                        "",
                        "[WARNING] x/spec.md: needs SHALL\n[ERROR] x/spec.md: needs a scenario\n"
                            + "[INFO] tip\n",
                        false)),
                new SpecItemReport("openspec spec auth", List.of())));

    assertThat(SpecReportRenderer.render(report))
        .isEqualTo(
            """
            openspec change foo: 1 error, 1 warning, 1 info
              ERROR x/spec.md: needs a scenario
              WARNING x/spec.md: needs SHALL
              INFO tip""");
  }

  @Test
  void emptyReportRendersNothing() {
    assertThat(SpecReportRenderer.render(SpecValidationReport.EMPTY)).isEmpty();
    assertThat(SpecValidationReport.EMPTY.hasFindings()).isFalse();
  }

  @Test
  void perItemLimitReportsOmittedFindings() {
    final List<SpecFinding> findings =
        new ArrayList<>(
            IntStream.range(0, 25)
                .mapToObj(i -> new SpecFinding(SpecFinding.Level.WARNING, "a.md", "w" + i))
                .toList());
    findings.add(new SpecFinding(SpecFinding.Level.ERROR, "a.md", "e"));
    final SpecValidationReport report =
        new SpecValidationReport(
            List.of(new SpecItemReport("openspec change foo", sorted(findings))));

    final String rendered = SpecReportRenderer.render(report);

    assertThat(rendered).startsWith("openspec change foo: 1 error, 25 warnings\n  ERROR a.md: e");
    assertThat(rendered).endsWith("  ... 6 more findings omitted");
  }

  @Test
  void totalLimitStopsBeforeOverflowingAndCountsTheRest() {
    final SpecFinding finding = new SpecFinding(SpecFinding.Level.ERROR, "f.md", "x".repeat(60));
    final List<SpecItemReport> items =
        IntStream.range(0, 40)
            .mapToObj(
                i -> new SpecItemReport("openspec change c" + i, Collections.nCopies(5, finding)))
            .toList();

    final String rendered = SpecReportRenderer.render(new SpecValidationReport(items));

    assertThat(rendered.length()).isLessThanOrEqualTo(SpecReportRenderer.MAX_CHARACTERS + 80);
    assertThat(rendered)
        .containsPattern("\\.\\.\\. \\d+ more findings omitted \\(output limit reached\\)$");
  }

  @Test
  void singleOversizedItemIsTruncated() {
    final SpecFinding huge = new SpecFinding(SpecFinding.Level.ERROR, null, "y".repeat(9_000));
    final String rendered =
        SpecReportRenderer.render(
            new SpecValidationReport(List.of(new SpecItemReport("openspec", List.of(huge)))));

    assertThat(rendered).endsWith("\n... output truncated");
    assertThat(rendered.length()).isLessThanOrEqualTo(SpecReportRenderer.MAX_CHARACTERS + 30);
  }

  private static List<SpecFinding> sorted(final List<SpecFinding> findings) {
    final List<SpecFinding> copy = new ArrayList<>(findings);
    copy.sort(Comparator.comparing(SpecFinding::level));
    return copy;
  }
}
