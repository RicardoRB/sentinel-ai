package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NativeHookResponseTest {
  private static final String BLOCKED = "blocked";

  @Test
  void translatesDenialsToHarnessNativeShapes() {
    assertThat(NativeHookCommand.denyResponse("codex", BLOCKED))
        .contains("PreToolUse", "permissionDecision", "deny");
    assertThat(NativeHookCommand.denyResponse("cursor", BLOCKED))
        .contains("permission", "deny", "agent_message");
    assertThat(NativeHookCommand.denyResponse("gemini", BLOCKED))
        .contains("decision", "deny", "reason");
    assertThat(NativeHookCommand.denyResponse("amp", BLOCKED))
        .contains("reject-and-continue", "blocked");
    assertThat(NativeHookCommand.denyResponse("copilot", BLOCKED))
        .contains("decision", "deny", "blocked");
    assertThat(NativeHookCommand.denyResponse("windsurf", BLOCKED))
        .contains("decision", "deny", "blocked");
    assertThat(NativeHookCommand.denyResponse("cline", BLOCKED))
        .contains("decision", "deny", "blocked");
    assertThat(NativeHookCommand.denyResponse("factory-droid", BLOCKED))
        .contains("decision", "deny", "blocked");
  }
}
