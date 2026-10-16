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
 * Native GitHub Copilot CLI hooks integration contract (Copilot CLI 1.0.98 hooks reference).
 *
 * <p>Events are registered under their VS Code compatible names, so Copilot sends the snake_case
 * payload ({@code hook_event_name}, {@code tool_name}, {@code tool_input}, {@code
 * stop_hook_active}). Output field names are the same in both formats.
 */
public final class CopilotIntegration implements NativeHarnessAdapter {
  /** Seconds Copilot waits for a quality check; a timed-out hook fails open. */
  private static final int CHECK_TIMEOUT_SECONDS = 600;

  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "copilot",
          ".github/hooks/sentinel.json",
          ".github/hooks/sentinel.sh",
          "PreToolUse,PostToolUse,Stop",
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
   * Copilot reads {@code version}, {@code disableAllHooks} and {@code hooks}, so the ownership
   * marker travels in each handler's documented {@code env} field. Matchers are anchored regular
   * expressions over the tool name. The command resolves the wrapper from the repository root so a
   * session started in a subdirectory still finds it.
   */
  @Override
  public String configContent() {
    return "{\n  \"version\": 1,\n  \"hooks\": {\n"
        + "    \"PreToolUse\": ["
        + handler("bash|edit|create", "pre", 0)
        + "],\n    \"PostToolUse\": ["
        + handler("edit|create", "post", CHECK_TIMEOUT_SECONDS)
        + "],\n    \"Stop\": ["
        + handler(null, "post", CHECK_TIMEOUT_SECONDS)
        + "]\n  }\n}\n";
  }

  private static String handler(final String matcher, final String phase, final int timeout) {
    return "{\"type\": \"command\""
        + (matcher == null ? "" : ", \"matcher\": \"" + matcher + "\"")
        + ", \"bash\": \"\\\"$(git rev-parse --show-toplevel 2>/dev/null || pwd)/"
        + DEFINITION.hookFile()
        + "\\\" "
        + phase
        + "\""
        + (timeout == 0 ? "" : ", \"timeoutSec\": " + timeout)
        + ", \"env\": {\"SENTINEL_OWNERSHIP\": \""
        + NativeHookText.marker(DEFINITION.id())
        + "\"}}";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  public String denyResponse(final String reason) {
    return "{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":"
        + NativeHookText.quote(reason)
        + "}";
  }

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

  /** Copilot only shows post-tool findings to the agent as {@code additionalContext}. */
  @Override
  public Optional<String> postEditFeedback(final String findings) {
    return Optional.of("{\"additionalContext\":" + NativeHookText.quote(findings) + "}");
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return NativeHookText.eventIn(input, Set.of("Stop"));
  }

  /** Copilot hook stdin in the VS Code compatible format. */
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
          arguments.path,
          arguments.command,
          null,
          null,
          false,
          Boolean.TRUE.equals(stopHookActive));
    }
  }

  /**
   * The arguments Sentinel inspects: {@code path} for edit and create, {@code command} for bash.
   */
  @JsonType
  public static final class ToolInput {
    public String path;
    public String command;
  }
}
