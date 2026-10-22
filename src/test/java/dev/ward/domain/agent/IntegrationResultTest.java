package dev.ward.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IntegrationResultTest {
  @Test
  void preservesLegacyConstructorWithNoCapabilities() {
    final IntegrationResult result =
        new IntegrationResult(IntegrationResult.Status.CHANGED, List.of("hook"), "installed");

    assertThat(result.capabilities()).isEmpty();
  }

  @Test
  void copiesCapabilitiesAndExposesStableValues() {
    final IntegrationResult result =
        new IntegrationResult(
            IntegrationResult.Status.CHANGED,
            List.of("hook"),
            "installed",
            Set.of(IntegrationCapability.PRE_TOOL, IntegrationCapability.FEEDBACK));

    assertThat(result.capabilities())
        .containsExactlyInAnyOrder(IntegrationCapability.PRE_TOOL, IntegrationCapability.FEEDBACK);
  }
}
