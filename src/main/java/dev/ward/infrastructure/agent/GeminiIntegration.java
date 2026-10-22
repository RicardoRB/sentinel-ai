package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.domain.json.JsonCodec;
import java.util.Optional;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/**
 * Native Gemini CLI hooks integration contract, read from {@code @google/gemini-cli-core} 0.64.0
 * ({@code hooks/hookRegistry.js}, {@code hooks/hookRunner.js}, {@code core/client.js}).
 */
public final class GeminiIntegration implements NativeHarnessAdapter {
  /** Milliseconds Gemini waits for a quality check; the default is 60 seconds. */
  private static final int CHECK_TIMEOUT_MILLIS = 600_000;

  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "gemini",
          ".gemini/settings.json",
          ".gemini/hooks/ward.sh",
          "BeforeTool,AfterTool,AfterAgent",
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

  /**
   * Gemini discards a definition without a {@code hooks} list, so every event is a matcher group.
   * Tool matchers are regular expressions over the tool name. The ownership marker is the
   * documented {@code description} of each hook. Commands run in the session's directory; the
   * wrapper is resolved from the repository root so a session started in a subdirectory still finds
   * it.
   */
  @Override
  public String configContent() {
    return "{\n  \"hooks\": {\n"
        + "    \"BeforeTool\": ["
        + group("^(write_file|replace|run_shell_command)$", "pre", 0)
        + "],\n    \"AfterTool\": ["
        + group("^(write_file|replace)$", "post", CHECK_TIMEOUT_MILLIS)
        + "],\n    \"AfterAgent\": ["
        + group(null, "post", CHECK_TIMEOUT_MILLIS)
        + "]\n  }\n}\n";
  }

  private static String group(final String matcher, final String phase, final int timeout) {
    return "{"
        + (matcher == null ? "" : "\"matcher\": \"" + matcher + "\", ")
        + "\"hooks\": [{\"type\": \"command\", \"name\": \"ward-"
        + phase
        + "\", \"command\": \"\\\"$(git rev-parse --show-toplevel 2>/dev/null || pwd)/"
        + DEFINITION.hookFile()
        + "\\\" "
        + phase
        + "\""
        + (timeout == 0 ? "" : ", \"timeout\": " + timeout)
        + ", \"description\": \""
        + NativeHookText.marker(DEFINITION.id())
        + "\"}]}";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  /** Returns the hook response that blocks the tool call and tells the agent {@code reason}. */
  public String denyResponse(final String reason) {
    return NativeHookText.genericDenial(reason);
  }

  /** A blocking AfterAgent decision makes Gemini retry the turn with the reason as the prompt. */
  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY -> denyResponse(outcome.message());
      case FEEDBACK ->
          "{\"decision\":\"block\",\"reason\":" + NativeHookText.quote(outcome.message()) + "}";
      case ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  /**
   * A blocking AfterTool result replaces the tool output with an error, although the edit was
   * applied, so findings are appended to the result as additional context instead.
   */
  @Override
  public Optional<String> postEditFeedback(final String findings) {
    return Optional.of(
        "{\"hookSpecificOutput\":{\"hookEventName\":\"AfterTool\",\"additionalContext\":"
            + NativeHookText.quote(findings)
            + "}}");
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return NativeHookText.eventIn(input, Set.of("AfterAgent"));
  }

  /** Gemini hook stdin: tool arguments arrive under {@code tool_input}. */
  @JsonType
  public static final class Payload {
    @JsonProperty("hook_event_name")
    public String hookEventName;

    @JsonProperty("tool_name")
    public String toolName;

    public String cwd;

    @JsonProperty("tool_input")
    public ToolInput toolInput;

    @JsonProperty("stop_hook_active")
    public Boolean stopHookActive;

    NativeHookInput input() {
      final ToolInput arguments = toolInput == null ? new ToolInput() : toolInput;
      return new NativeHookInput(
          hookEventName,
          toolName,
          cwd == null ? "" : cwd,
          arguments.filePath,
          arguments.command,
          null,
          null,
          false,
          Boolean.TRUE.equals(stopHookActive));
    }
  }

  /** {@code file_path} for write_file and replace, {@code command} for run_shell_command. */
  @JsonType
  public static final class ToolInput {
    @JsonProperty("file_path")
    public String filePath;

    public String command;
  }
}
