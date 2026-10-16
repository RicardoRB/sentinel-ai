package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.infrastructure.agent.CodexIntegration;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import org.junit.jupiter.api.Test;

/** Pins the Codex 0.163.0 hook stdin shape: tool arguments arrive under {@code tool_input}. */
class NativeHookPayloadTest {
  private static NativeHookInput parse(final String payload) {
    return new CodexIntegration().parse(new ForyJsonCodec(), payload);
  }

  @Test
  void readsTheShellCommandFromToolInput() {
    final NativeHookInput event =
        parse(
            "{\"session_id\":\"s\",\"turn_id\":\"t\",\"cwd\":\"/p\",\"hook_event_name\":\"PreToolUse\","
                + "\"tool_name\":\"Bash\",\"tool_use_id\":\"u\",\"tool_input\":{\"command\":\"git status\"}}");

    assertThat(event.eventName()).isEqualTo("PreToolUse");
    assertThat(event.toolName()).isEqualTo("Bash");
    assertThat(event.workingDirectory()).isEqualTo("/p");
    assertThat(event.command()).isEqualTo("git status");
    assertThat(event.patch()).isNull();
  }

  @Test
  void readsThePatchTextOfApplyPatchFromToolInput() {
    final NativeHookInput event =
        parse(
            "{\"hook_event_name\":\"PostToolUse\",\"tool_name\":\"apply_patch\","
                + "\"tool_input\":{\"command\":\"*** Begin Patch\"},\"tool_response\":\"ok\"}");

    assertThat(event.patch()).isEqualTo("*** Begin Patch");
    assertThat(event.command()).isNull();
  }

  @Test
  void readsTheStopLoopFlag() {
    assertThat(parse("{\"hook_event_name\":\"Stop\",\"stop_hook_active\":true}").continued())
        .isTrue();
    assertThat(parse("{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}").continued())
        .isFalse();
  }
}
