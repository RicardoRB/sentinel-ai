package dev.sentinel.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class QualityGatePhasePolicyTest {
  @Test
  void assignsFastGatesToDefaultAndAllLocalPhases() {
    assertThat(QualityGatePhasePolicy.profilesFor("format"))
        .containsExactly("default", "pre-commit", "pre-push", "ci");
    assertThat(QualityGatePhasePolicy.profilesFor("compile")).containsExactly("pre-push", "ci");
    assertThat(QualityGatePhasePolicy.profilesFor("checkstyle"))
        .containsExactly("default", "pre-commit", "pre-push", "ci");
    assertThat(QualityGatePhasePolicy.profilesFor("pmd"))
        .containsExactly("default", "pre-commit", "pre-push", "ci");
    assertThat(QualityGatePhasePolicy.profilesFor("gitleaks"))
        .containsExactly("default", "pre-commit", "pre-push", "ci");
  }

  @Test
  void assignsSlowerLocalGatesToPushAndCi() {
    for (final String gate :
        List.of("tests", "coverage", "spotbugs", "archunit", "enforcer", "license")) {
      assertThat(QualityGatePhasePolicy.profilesFor(gate)).containsExactly("pre-push", "ci");
    }
  }

  @Test
  void assignsUnknownCostCommandsToPushAndCi() {
    assertThat(QualityGatePhasePolicy.profilesFor("command")).containsExactly("pre-push", "ci");
  }

  @Test
  void assignsUnclassifiedGatesToCiOnly() {
    assertThat(QualityGatePhasePolicy.profilesFor("mutation")).containsExactly("ci");
    assertThat(QualityGatePhasePolicy.profilesFor("future-gate")).containsExactly("ci");
  }
}
