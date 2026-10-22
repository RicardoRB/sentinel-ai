package dev.ward.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeverityThresholdTest {
  @Test
  void includesEveryThresholdAndSeverityPair() {
    assertThat(SeverityThreshold.ERROR.includes(FindingSeverity.ERROR)).isTrue();
    assertThat(SeverityThreshold.ERROR.includes(FindingSeverity.WARNING)).isFalse();
    assertThat(SeverityThreshold.ERROR.includes(FindingSeverity.NOTE)).isFalse();
    assertThat(SeverityThreshold.WARNING.includes(FindingSeverity.ERROR)).isTrue();
    assertThat(SeverityThreshold.WARNING.includes(FindingSeverity.WARNING)).isTrue();
    assertThat(SeverityThreshold.WARNING.includes(FindingSeverity.NOTE)).isFalse();
    assertThat(SeverityThreshold.INFO.includes(FindingSeverity.ERROR)).isTrue();
    assertThat(SeverityThreshold.INFO.includes(FindingSeverity.WARNING)).isTrue();
    assertThat(SeverityThreshold.INFO.includes(FindingSeverity.NOTE)).isTrue();
  }

  @Test
  void onlyInfoShowsCapturedOutput() {
    assertThat(SeverityThreshold.ERROR.showsCapturedOutput()).isFalse();
    assertThat(SeverityThreshold.WARNING.showsCapturedOutput()).isFalse();
    assertThat(SeverityThreshold.INFO.showsCapturedOutput()).isTrue();
  }
}
