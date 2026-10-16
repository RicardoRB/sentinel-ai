package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parses each native adapter's hook payload through its generated JSON codec, which is what the
 * native executable uses. Codex and Copilot have their own tool_input contracts, covered by
 * NativeHookPayloadTest and NativeHookCopilotTest.
 */
class NativeAdapterPayloadTest {
  static Stream<NativeHarnessAdapter> adapters() {
    return Stream.of(
        new AmpIntegration(),
        new ClineIntegration(),
        new CursorIntegration(),
        new FactoryDroidIntegration(),
        new GeminiIntegration(),
        new WindsurfIntegration());
  }

  private static NativeHookInput parse(final NativeHarnessAdapter adapter, final String payload) {
    return adapter.parse(new ForyJsonCodec(), payload);
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void readsPrimaryFieldNames(final NativeHarnessAdapter adapter) {
    final NativeHookInput event =
        parse(
            adapter,
            "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Edit\",\"cwd\":\"/p\","
                + "\"file_path\":\"openspec/specs/a/spec.md\",\"command\":\"ls\","
                + "\"patch\":\"*** Begin Patch\",\"skill\":\"archive\"}");

    assertThat(event.eventName()).isEqualTo("PreToolUse");
    assertThat(event.toolName()).isEqualTo("Edit");
    assertThat(event.workingDirectory()).isEqualTo("/p");
    assertThat(event.path()).isEqualTo("openspec/specs/a/spec.md");
    assertThat(event.command()).isEqualTo("ls");
    assertThat(event.patch()).isEqualTo("*** Begin Patch");
    assertThat(event.skill()).isEqualTo("archive");
    assertThat(event.unreadable()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void readsAlternateFieldNames(final NativeHarnessAdapter adapter) {
    final NativeHookInput event =
        parse(
            adapter,
            "{\"event\":\"AfterTool\",\"event_name\":\"ignored\",\"type\":\"ignored\","
                + "\"name\":\"write_file\",\"path\":\"src/A.java\",\"patchText\":\"diff\"}");

    assertThat(event.eventName()).isEqualTo("AfterTool");
    assertThat(event.toolName()).isEqualTo("write_file");
    assertThat(event.workingDirectory()).isEmpty();
    assertThat(event.path()).isEqualTo("src/A.java");
    assertThat(event.patch()).isEqualTo("diff");
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void treatsJsonNullAsUnreadable(final NativeHarnessAdapter adapter) {
    assertThat(parse(adapter, "null").unreadable()).isTrue();
  }
}
