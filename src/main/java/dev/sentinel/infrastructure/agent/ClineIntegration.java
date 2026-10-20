package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.json.JsonCodecException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fory.json.annotation.JsonType;

/**
 * Native Cline v3 hooks integration contract, read from {@code @cline/core} 0.0.94. Cline runs each
 * file in {@code .cline/hooks/} named after an event with {@code bash}, waits for PreToolUse and
 * PostToolUse and reads their JSON control output; TaskComplete runs detached and its output is
 * ignored, so there is no turn-end delivery.
 */
public final class ClineIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "cline",
          ".cline/hooks/PostToolUse",
          ".cline/hooks/PreToolUse",
          "PreToolUse,PostToolUse",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.POST_EDIT,
              IntegrationCapability.FEEDBACK,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD),
          ".cline/hooks/sentinel.sh");

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  /** The PostToolUse hook file, which is this integration's configuration artifact. */
  @Override
  public String configContent() {
    return ClineHookScripts.postToolUse(NativeHookText.marker(DEFINITION.id()));
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return ClineHookScripts.preToolUse(NativeHookText.marker(DEFINITION.id()), guardMode);
  }

  /** Returns the hook response that blocks the tool call and tells the agent {@code reason}. */
  public String denyResponse(final String reason) {
    return "{\"cancel\":true,\"errorMessage\":" + NativeHookText.quote(reason) + "}";
  }

  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY -> denyResponse(outcome.message());
      case FEEDBACK -> postEditFeedback(outcome.message()).orElseThrow();
      case ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  /** Cline appends PostToolUse {@code contextModification} to the tool result. */
  @Override
  public Optional<String> postEditFeedback(final String findings) {
    return Optional.of("{\"contextModification\":" + NativeHookText.quote(findings) + "}");
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    final Payload event = codec.fromJson(payload, Payload.class);
    if (event == null) {
      return NativeHookInput.unreadableInput();
    }
    final ToolUse tool = event.preToolUse == null ? event.postToolUse : event.preToolUse;
    final Parameters parameters =
        tool == null || tool.parameters == null ? new Parameters() : tool.parameters;
    final String command;
    try {
      command = commandText(codec, parameters.commands);
    } catch (JsonCodecException exception) {
      return NativeHookInput.unreadableInput();
    }
    return new NativeHookInput(
        event.hookName,
        tool == null ? null : tool.toolName,
        "",
        parameters.path,
        command,
        parameters.input,
        null,
        false,
        false);
  }

  /** run_commands sends a list of command lines, serialized as JSON in the parameters. */
  private static String commandText(final JsonCodec codec, final String commands) {
    if (commands == null || commands.isBlank()) {
      return null;
    }
    final CommandList list = codec.fromJson("{\"commands\":" + commands + "}", CommandList.class);
    return list == null || list.commands == null ? null : String.join("\n", list.commands);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return false;
  }

  /** Cline hook stdin; tool parameters arrive as strings, objects serialized as JSON. */
  @JsonType
  public static final class Payload {
    public String hookName;
    public ToolUse preToolUse;
    public ToolUse postToolUse;
  }

  /** The tool call in a Cline hook payload; its public fields mirror the event JSON. */
  @JsonType
  public static final class ToolUse {
    public String toolName;
    public Parameters parameters;
  }

  /** {@code path} for editor, {@code input} for apply_patch, {@code commands} for run_commands. */
  @JsonType
  public static final class Parameters {
    public String path;
    public String input;
    public String commands;
  }

  /** The commands of a Cline tool call; its public field mirrors the event JSON. */
  @JsonType
  public static final class CommandList {
    public List<String> commands;
  }
}
