package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.json.ForyJsonCodec;
import org.junit.jupiter.api.Test;

class NativeHookPayloadTest {
  @Test
  void acceptsCommonEventAndToolFieldNames() {
    final NativeHookCommand.NativeHookEvent event =
        new ForyJsonCodec()
            .fromJson(
                "{\"event\":\"AfterTool\",\"tool_name\":\"write_file\",\"file_path\":\"openspec/spec.md\"}",
                NativeHookCommand.NativeHookEvent.class);

    assertThat(event.eventName()).isEqualTo("AfterTool");
    assertThat(event.toolName()).isEqualTo("write_file");
    assertThat(event.path()).isEqualTo("openspec/spec.md");
  }

  @Test
  void acceptsPatchAndShellPayloadFields() {
    final NativeHookCommand.NativeHookEvent event =
        new ForyJsonCodec()
            .fromJson(
                "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Bash\",\"command\":\"git status\",\"patchText\":\"diff\"}",
                NativeHookCommand.NativeHookEvent.class);

    assertThat(event.eventName()).isEqualTo("PreToolUse");
    assertThat(event.command).isEqualTo("git status");
    assertThat(event.patch()).isEqualTo("diff");
  }
}
