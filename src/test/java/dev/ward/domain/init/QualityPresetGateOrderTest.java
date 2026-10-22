package dev.ward.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class QualityPresetGateOrderTest {
  @Test
  void explicitGatesFollowPresetGatesWithoutDuplicates() {
    assertThat(QualityPreset.STANDARD.withExplicitGates(List.of("gitleaks", "tests")))
        .containsExactlyElementsOf(
            Stream.concat(QualityPreset.STANDARD.gates().stream(), Stream.of("gitleaks")).toList());
  }

  @Test
  void explicitGatesKeepTheirGivenOrder() {
    assertThat(QualityPreset.STANDARD.withExplicitGates(List.of("sonar", "gitleaks", "sonar")))
        .endsWith("sonar", "gitleaks");
  }

  @Test
  void noExplicitGatesReturnsThePresetGateSet() {
    assertThat(QualityPreset.STRICT.withExplicitGates(List.of()))
        .containsExactlyElementsOf(QualityPreset.STRICT.gates());
  }

  @Test
  void selectedPresetGatesComeFirstInPresetOrder() {
    assertThat(QualityPreset.STANDARD.orderSelected(List.of("gitleaks", "pmd", "tests")))
        .containsExactly("tests", "pmd", "gitleaks");
  }

  @Test
  void unselectedPresetGatesAreNotAdded() {
    assertThat(QualityPreset.STRICT.orderSelected(List.of("compile"))).containsExactly("compile");
  }
}
