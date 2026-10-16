package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Optional;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/**
 * Native Cursor hooks integration contract, read from cursor-agent 2026.10.14-941492e: hooks.json
 * needs {@code version} and known event names, every hook is a flat command entry, project hooks
 * run from the project root, and file changes reach hooks as the {@code Write} and {@code Delete}
 * tools.
 */
public final class CursorIntegration implements NativeHarnessAdapter {
  /** Seconds Cursor waits for a quality check. */
  private static final int CHECK_TIMEOUT_SECONDS = 600;

  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "cursor",
          ".cursor/hooks.json",
          ".cursor/hooks/sentinel.sh",
          "preToolUse,postToolUse,stop",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.POST_EDIT,
              IntegrationCapability.TURN_END,
              IntegrationCapability.FEEDBACK,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD));

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  /** Cursor validates only {@code version} and {@code hooks}, so the marker is a top-level key. */
  @Override
  public String configContent() {
    return "{\n  \"version\": 1,\n  \"sentinelOwnership\": \""
        + NativeHookText.marker(DEFINITION.id())
        + "\",\n  \"hooks\": {\n"
        + "    \"preToolUse\": ["
        + hook("^(Shell|Write|Delete)$", "pre", 0)
        + "],\n    \"postToolUse\": ["
        + hook("^(Write|Delete)$", "post", CHECK_TIMEOUT_SECONDS)
        + "],\n    \"stop\": ["
        + hook(null, "post", CHECK_TIMEOUT_SECONDS)
        + "]\n  }\n}\n";
  }

  private static String hook(final String matcher, final String phase, final int timeout) {
    return "{\"command\": \""
        + DEFINITION.hookFile()
        + " "
        + phase
        + "\""
        + (matcher == null ? "" : ", \"matcher\": \"" + matcher + "\"")
        + (timeout == 0 ? "" : ", \"timeout\": " + timeout)
        + "}";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  public String denyResponse(final String reason) {
    return "{\"permission\":\"deny\",\"user_message\":"
        + NativeHookText.quote(reason)
        + ",\"agent_message\":"
        + NativeHookText.quote(reason)
        + "}";
  }

  /** Turn-end findings are submitted as the next prompt with {@code followup_message}. */
  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY -> denyResponse(outcome.message());
      case FEEDBACK -> "{\"followup_message\":" + NativeHookText.quote(outcome.message()) + "}";
      case ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  /** Cursor injects postToolUse {@code additional_context} into the conversation. */
  @Override
  public Optional<String> postEditFeedback(final String findings) {
    return Optional.of("{\"additional_context\":" + NativeHookText.quote(findings) + "}");
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return NativeHookText.eventIn(input, Set.of("stop"));
  }

  /** Cursor hook stdin; {@code loop_count} counts earlier stop follow-ups in the conversation. */
  @JsonType
  public static final class Payload {
    @JsonProperty("hook_event_name")
    public String hookEventName;

    @JsonProperty("tool_name")
    public String toolName;

    public String cwd;

    @JsonProperty("tool_input")
    public ToolInput toolInput;

    @JsonProperty("loop_count")
    public Integer loopCount;

    NativeHookInput input() {
      final ToolInput arguments = toolInput == null ? new ToolInput() : toolInput;
      final String directory =
          arguments.workingDirectory == null ? cwd : arguments.workingDirectory;
      return new NativeHookInput(
          hookEventName,
          toolName,
          directory == null ? "" : directory,
          arguments.filePath,
          arguments.command,
          null,
          null,
          false,
          loopCount != null && loopCount > 0);
    }
  }

  /** {@code file_path} for Write and Delete; {@code command} and its directory for Shell. */
  @JsonType
  public static final class ToolInput {
    @JsonProperty("file_path")
    public String filePath;

    public String command;

    @JsonProperty("working_directory")
    public String workingDirectory;
  }
}
