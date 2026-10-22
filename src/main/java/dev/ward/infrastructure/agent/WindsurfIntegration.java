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
 * Native Windsurf (Cascade) hooks integration contract. Pre-hooks block with exit status 2 and the
 * agent sees stderr; post-hooks cannot reach the agent, so post-write findings are shown to the
 * user in the Cascade UI and there is no turn-end delivery.
 */
public final class WindsurfIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "windsurf",
          ".windsurf/hooks.json",
          ".windsurf/hooks/ward.sh",
          "pre_write_code,pre_run_command,post_write_code",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.POST_EDIT,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD));

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  /**
   * Hooks run through {@code bash -c} from the workspace root. The documented hook fields are
   * {@code command}, {@code powershell}, {@code show_output} and {@code working_directory}, so the
   * ownership marker is a shell comment at the end of each command.
   */
  @Override
  public String configContent() {
    return "{\n  \"hooks\": {\n"
        + "    \"pre_write_code\": ["
        + hook("pre", false)
        + "],\n    \"pre_run_command\": ["
        + hook("pre", false)
        + "],\n    \"post_write_code\": ["
        + hook("post", true)
        + "]\n  }\n}\n";
  }

  private static String hook(final String phase, final boolean showOutput) {
    return "{\"command\": \""
        + DEFINITION.hookFile()
        + " "
        + phase
        + " # "
        + NativeHookText.marker(DEFINITION.id())
        + "\""
        + (showOutput ? ", \"show_output\": true" : "")
        + "}";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  /** Returns {@code reason} unchanged: Windsurf shows the text a blocking hook prints. */
  public String denyResponse(final String reason) {
    return reason;
  }

  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY, FEEDBACK, ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  @Override
  public boolean deniesWithExitStatus() {
    return true;
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return false;
  }

  /** Cascade hook stdin: event data arrives under {@code tool_info}. */
  @JsonType
  public static final class Payload {
    @JsonProperty("agent_action_name")
    public String agentActionName;

    @JsonProperty("tool_info")
    public ToolInfo toolInfo;

    NativeHookInput input() {
      final ToolInfo info = toolInfo == null ? new ToolInfo() : toolInfo;
      return new NativeHookInput(
          agentActionName,
          agentActionName,
          info.cwd == null ? "" : info.cwd,
          info.filePath,
          info.commandLine,
          null,
          null,
          false,
          false);
    }
  }

  /** {@code file_path} for code events; {@code command_line} and {@code cwd} for commands. */
  @JsonType
  public static final class ToolInfo {
    @JsonProperty("file_path")
    public String filePath;

    @JsonProperty("command_line")
    public String commandLine;

    public String cwd;
  }
}
