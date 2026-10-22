package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.AgentPayloadFixtures;
import dev.ward.domain.spec.GuardRequest;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Recorded Claude Code {@code PreToolUse} payloads as the approved-artifact guard reads them. */
class ClaudeHookGuardPayloadTest {
  private static final int GUARD_PAYLOAD_LIMIT = 16 * 1024 * 1024;
  private static final Path PROJECT = Path.of("/work/project");
  private static final ForyJsonCodec CODEC = new ForyJsonCodec();

  static String fixture(final String name, final Path project) {
    return AgentPayloadFixtures.text("claude-code/" + name)
        .replace("__PROJECT__", project.toString());
  }

  private static Optional<ClaudeHookEvent> parse(final String payload) {
    return ClaudeHookEvent.readComplete(
        new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)), CODEC);
  }

  private static GuardRequest request(final String name) {
    final ClaudeHookEvent event = parse(fixture(name, PROJECT)).orElseThrow();
    assertThat(event.isPreToolUse()).isTrue();
    assertThat(event.cwd).isEqualTo(PROJECT.toString());
    return ClaudePreToolGuard.request(PROJECT, event).orElseThrow();
  }

  @Test
  void fileToolsBecomeWrittenPaths() {
    assertThat(request("pre-tool-write-approved-spec.json"))
        .isEqualTo(
            GuardRequest.paths(
                PROJECT, PROJECT.toString(), List.of(PROJECT + "/openspec/specs/x/spec.md")));
    assertThat(request("pre-tool-edit-active-change.json").values())
        .containsExactly(PROJECT + "/openspec/changes/foo/design.md");
    assertThat(request("pre-tool-multiedit-archive.json").values())
        .containsExactly(PROJECT + "/openspec/changes/archive/2026-10-05-old/tasks.md");
    assertThat(request("pre-tool-notebook-edit.json").values())
        .containsExactly(PROJECT + "/openspec/specs/x/analysis.ipynb");
  }

  @Test
  void bashAndSkillBecomeShellAndSkillRequests() {
    assertThat(request("pre-tool-bash-archive.json"))
        .isEqualTo(
            GuardRequest.shell(
                PROJECT,
                PROJECT.toString(),
                "openspec validate foo --strict && openspec archive foo -y"));
    assertThat(request("pre-tool-skill-archive.json"))
        .isEqualTo(GuardRequest.skill(PROJECT, "opsx:archive"));
  }

  @Test
  void otherToolsAndEventsAreNotGuarded() {
    final ClaudeHookEvent read =
        parse(
                """
                {"hook_event_name":"PreToolUse","tool_name":"Read",
                 "tool_input":{"file_path":"/work/project/openspec/specs/x/spec.md"}}
                """)
            .orElseThrow();
    assertThat(ClaudePreToolGuard.request(PROJECT, read)).isEmpty();
    assertThat(parse(fixture("stop.json", PROJECT)).orElseThrow().isPreToolUse()).isFalse();
    assertThat(
            ClaudePreToolGuard.request(
                PROJECT,
                parse("{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Write\"}")
                    .orElseThrow()))
        .contains(GuardRequest.paths(PROJECT, "", List.of()));
  }

  @Test
  void largeWritePayloadIsReadInFull() {
    final String content = "x".repeat(4 * 1024 * 1024);
    final String payload =
        """
        {"hook_event_name":"PreToolUse","tool_name":"Write",
         "tool_input":{"content":"%s","file_path":"/work/project/openspec/specs/x/spec.md"}}
        """
            .formatted(content);

    assertThat(parse(payload).orElseThrow().input().orElseThrow().filePath)
        .isEqualTo("/work/project/openspec/specs/x/spec.md");
  }

  @Test
  void unreadablePayloadsAreEmpty() {
    assertThat(parse("")).isEmpty();
    assertThat(parse("not json")).isEmpty();
    assertThat(parse("[1, 2]")).isEmpty();
    assertThat(parse("null")).isEmpty();
    final byte[] oversized = new byte[GUARD_PAYLOAD_LIMIT + 1];
    assertThat(ClaudeHookEvent.readComplete(new ByteArrayInputStream(oversized), CODEC)).isEmpty();
    assertThat(
            ClaudeHookEvent.readComplete(
                new InputStream() {
                  @Override
                  public int read() throws IOException {
                    throw new IOException("closed");
                  }
                },
                CODEC))
        .isEmpty();
  }
}
