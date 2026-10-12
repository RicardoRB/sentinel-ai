package dev.sentinel.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.agent.IntegrationRequest.Guidance;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationRequestTest {
  @Test
  void guidanceSelectionDecidesWhichTargetsAreCovered() {
    assertThat(IntegrationRequest.install(Guidance.EXCLUDED).coversHooks()).isTrue();
    assertThat(IntegrationRequest.install(Guidance.EXCLUDED).coversGuidance()).isFalse();
    assertThat(IntegrationRequest.install(Guidance.INCLUDED).coversHooks()).isTrue();
    assertThat(IntegrationRequest.install(Guidance.INCLUDED).coversGuidance()).isTrue();
    assertThat(IntegrationRequest.remove(Guidance.ONLY).coversHooks()).isFalse();
    assertThat(IntegrationRequest.remove(Guidance.ONLY).coversGuidance()).isTrue();
    assertThat(IntegrationRequest.remove(Guidance.ONLY).removal()).isTrue();
    assertThat(IntegrationRequest.install(Guidance.ONLY).removal()).isFalse();
  }

  @Test
  void rejectsMissingParts() {
    assertThatThrownBy(() -> new IntegrationRequest(null, Guidance.EXCLUDED))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new IntegrationRequest(IntegrationRequest.Operation.INSTALL, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resultsCarryWarningsAndReportFailures() {
    final IntegrationResult changed =
        new IntegrationResult(IntegrationResult.Status.CHANGED, List.of("a"), "done");
    assertThat(changed.warnings()).isEmpty();
    assertThat(changed.failed()).isFalse();
    assertThat(changed.withWarning("first").withWarning("second").warnings())
        .containsExactly("first", "second");
    assertThat(new IntegrationResult(IntegrationResult.Status.UNAVAILABLE, List.of(), "x").failed())
        .isTrue();
    assertThat(new IntegrationResult(IntegrationResult.Status.CONFLICT, List.of(), "x").failed())
        .isTrue();
  }
}
