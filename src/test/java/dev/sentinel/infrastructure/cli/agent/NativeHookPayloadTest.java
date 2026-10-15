package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.infrastructure.agent.CodexIntegration;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import org.junit.jupiter.api.Test;

class NativeHookPayloadTest {
  @Test
  void acceptsCommonEventAndToolFieldNames() {
    final NativeHookInput event =
        new CodexIntegration()
            .parse(
                new ForyJsonCodec(),
                "{\"event\":\"AfterTool\",\"tool_name\":\"write_file\",\"file_path\":\"openspec/spec.md\"}");

    assertThat(event.eventName()).isEqualTo("AfterTool");
    assertThat(event.toolName()).isEqualTo("write_file");
    assertThat(event.path()).isEqualTo("openspec/spec.md");
  }

  @Test
  void acceptsPatchAndShellPayloadFields() {
    final NativeHookInput event =
        new CodexIntegration()
            .parse(
                new ForyJsonCodec(),
                "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Bash\",\"command\":\"git status\",\"patchText\":\"diff\"}");

    assertThat(event.eventName()).isEqualTo("PreToolUse");
    assertThat(event.command()).isEqualTo("git status");
    assertThat(event.patch()).isEqualTo("diff");
  }
}
