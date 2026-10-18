package dev.sentinel.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GateOutputVisibilityTest {
  @Test
  void appliesTheSharedVisibilityTable() {
    final GateResult passed = result(GateStatus.PASSED, GateFindings.NONE);
    final GateResult visible = result(GateStatus.FAILED, GateFindings.of(List.of(finding())));
    final GateResult hidden =
        result(
            GateStatus.FAILED,
            GateFindings.of(
                List.of(
                    new Finding(
                        "g", "t", "r", FindingSeverity.NOTE, "m", null, null, null, null))));
    final GateResult empty = result(GateStatus.FAILED, GateFindings.NONE);

    assertThat(GateOutputVisibility.of(passed, SeverityThreshold.WARNING))
        .isEqualTo(GateOutputVisibility.NONE);
    assertThat(GateOutputVisibility.of(visible, SeverityThreshold.WARNING))
        .isEqualTo(GateOutputVisibility.FINDINGS);
    assertThat(GateOutputVisibility.of(hidden, SeverityThreshold.ERROR))
        .isEqualTo(GateOutputVisibility.TAIL);
    assertThat(GateOutputVisibility.of(empty, SeverityThreshold.WARNING))
        .isEqualTo(GateOutputVisibility.TAIL);
    assertThat(GateOutputVisibility.of(visible, SeverityThreshold.INFO))
        .isEqualTo(GateOutputVisibility.FULL);
  }

  private static GateResult result(final GateStatus status, final GateFindings findings) {
    return new GateResult("g", status, List.of("g"), 1, Duration.ZERO, "out", "err")
        .withFindings(findings);
  }

  private static Finding finding() {
    return new Finding("g", "t", "r", FindingSeverity.ERROR, "m", null, null, null, null);
  }
}
