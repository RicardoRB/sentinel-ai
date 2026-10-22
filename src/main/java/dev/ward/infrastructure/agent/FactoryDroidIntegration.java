package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.domain.json.JsonCodec;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/**
 * Native Factory Droid hooks integration contract, read from droid 0.243.0: the project's {@code
 * .factory/hooks.json} holds the events at its top level and is validated as a whole, so an invalid
 * file loads no hooks.
 */
public final class FactoryDroidIntegration implements NativeHarnessAdapter {
  /** Seconds Droid waits for a quality check; the default is 60 seconds. */
  private static final int CHECK_TIMEOUT_SECONDS = 600;

  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "factory-droid",
          ".factory/hooks.json",
          ".factory/hooks/ward.sh",
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
   * Hook entries accept only {@code type}, {@code command}, {@code timeout} and {@code
   * preserveRiskLevel}, so the ownership marker is a shell comment at the end of each command.
   * Commands run in Droid's working directory; the wrapper is resolved from the repository root.
   */
  @Override
  public String configContent() {
    return "{\n"
        + "  \"PreToolUse\": ["
        + group("^(Create|Edit|MultiEdit|ApplyPatch|Execute)$", "pre", 0)
        + "],\n  \"PostToolUse\": ["
        + group("^(Create|Edit|MultiEdit|ApplyPatch)$", "post", CHECK_TIMEOUT_SECONDS)
        + "],\n  \"Stop\": ["
        + group(null, "post", CHECK_TIMEOUT_SECONDS)
        + "]\n}\n";
  }

  private static String group(final String matcher, final String phase, final int timeout) {
    return "{"
        + (matcher == null ? "" : "\"matcher\": \"" + matcher + "\", ")
        + "\"hooks\": [{\"type\": \"command\", \"command\": \"\\\"$(git rev-parse --show-toplevel 2>/dev/null || pwd)/"
        + DEFINITION.hookFile()
        + "\\\" "
        + phase
        + " # "
        + NativeHookText.marker(DEFINITION.id())
        + "\""
        + (timeout == 0 ? "" : ", \"timeout\": " + timeout)
        + "}]}";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  /** Returns the hook response that blocks the tool call and tells the agent {@code reason}. */
  public String denyResponse(final String reason) {
    return "{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\",\"permissionDecision\":\"deny\",\"permissionDecisionReason\":"
        + NativeHookText.quote(reason)
        + "}}";
  }

  /** Post-tool findings exit 2, which Droid feeds to the model; Stop continues with block. */
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

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return NativeHookText.eventIn(input, Set.of("Stop"));
  }

  /** Droid hook stdin: tool arguments arrive under {@code tool_input}. */
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
          arguments.input,
          null,
          false,
          Boolean.TRUE.equals(stopHookActive));
    }
  }

  /** {@code file_path} for Create and Edit, {@code input} (the patch) for ApplyPatch. */
  @JsonType
  public static final class ToolInput {
    @JsonProperty("file_path")
    public String filePath;

    public String command;
    public String input;
  }
}
