package dev.ward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.agent.AgentRequest;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.cli.VersionProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

// This test intentionally aggregates coverage across the application services.
// Splitting it would duplicate setup and obscure the cross-service scenarios.
class CoverageCliTest {
  @Test
  void versionProviderSupportsRuntimeAndInjectedVersions() {
    assertThat(new VersionProvider().getVersion())
        .containsExactly("ward " + VersionProvider.current());
    assertThat(new VersionProvider("1.2.3").getVersion()).containsExactly("ward 1.2.3");
  }

  @Test
  void validatesProcessAndAgentResults() {
    final CommandResult success = new CommandResult(0, "out", "err", Duration.ZERO);
    assertThat(success.succeeded()).isTrue();
    assertThat(success.hasExecutionError()).isFalse();
    assertThat(new CommandResult(1, "", "", Duration.ZERO, "failed").hasExecutionError()).isTrue();
    assertThatThrownBy(() -> new AgentRequest("", 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AgentRequest("task", 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
