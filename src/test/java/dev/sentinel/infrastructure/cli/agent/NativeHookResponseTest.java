package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NativeHookResponseTest {
  @Test
  void translatesDenialsToHarnessNativeShapes() {
    assertThat(NativeHookCommand.denyResponse("codex", "blocked"))
        .contains("PreToolUse", "permissionDecision", "deny");
    assertThat(NativeHookCommand.denyResponse("cursor", "blocked"))
        .contains("permission", "deny", "agent_message");
    assertThat(NativeHookCommand.denyResponse("gemini", "blocked"))
        .contains("decision", "deny", "reason");
    assertThat(NativeHookCommand.denyResponse("amp", "blocked"))
        .contains("reject-and-continue", "blocked");
  }
}
