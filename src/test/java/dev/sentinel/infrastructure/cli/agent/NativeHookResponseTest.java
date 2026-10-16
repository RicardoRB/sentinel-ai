package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.agent.AmpIntegration;
import dev.sentinel.infrastructure.agent.ClineIntegration;
import dev.sentinel.infrastructure.agent.CodexIntegration;
import dev.sentinel.infrastructure.agent.CopilotIntegration;
import dev.sentinel.infrastructure.agent.CursorIntegration;
import dev.sentinel.infrastructure.agent.FactoryDroidIntegration;
import dev.sentinel.infrastructure.agent.GeminiIntegration;
import dev.sentinel.infrastructure.agent.WindsurfIntegration;
import org.junit.jupiter.api.Test;

class NativeHookResponseTest {
  private static final String BLOCKED = "blocked";

  @Test
  void translatesDenialsToHarnessNativeShapes() {
    assertThat(new CodexIntegration().denyResponse(BLOCKED))
        .contains("PreToolUse", "permissionDecision", "deny");
    assertThat(new CursorIntegration().denyResponse(BLOCKED))
        .contains("permission", "deny", "agent_message");
    assertThat(new GeminiIntegration().denyResponse(BLOCKED))
        .contains("decision", "deny", "reason");
    assertThat(new AmpIntegration().denyResponse(BLOCKED))
        .contains("reject-and-continue", "blocked");
    assertThat(new CopilotIntegration().denyResponse(BLOCKED))
        .contains("permissionDecision", "deny", "blocked");
    assertThat(new WindsurfIntegration().denyResponse(BLOCKED)).isEqualTo(BLOCKED);
    assertThat(new ClineIntegration().denyResponse(BLOCKED))
        .contains("decision", "deny", "blocked");
    assertThat(new FactoryDroidIntegration().denyResponse(BLOCKED))
        .contains("permissionDecision", "deny", "blocked");
  }
}
