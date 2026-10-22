package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.json.JsonCodec;
import org.apache.fory.json.annotation.JsonType;

/** JSON responses understood by Claude Code hooks. */
final class ClaudeHookResponses {
  private ClaudeHookResponses() {}

  /** Context added to the agent after a successful tool call. */
  static String postToolUseContext(final JsonCodec codec, final String context) {
    final ClaudeCodeHookCommand.HookOutput output = new ClaudeCodeHookCommand.HookOutput();
    output.hookSpecificOutput = new ClaudeCodeHookCommand.HookSpecificOutput();
    output.hookSpecificOutput.hookEventName = "PostToolUse";
    output.hookSpecificOutput.additionalContext = context;
    return codec.toJson(output);
  }

  /** Prevents Claude from stopping and tells it why. */
  static String blockStop(final JsonCodec codec, final String reason) {
    final StopBlockOutput output = new StopBlockOutput();
    output.decision = "block";
    output.reason = reason;
    return codec.toJson(output);
  }

  /** A warning shown to the user without affecting the agent. */
  static String systemMessage(final JsonCodec codec, final String message) {
    final SystemMessageOutput output = new SystemMessageOutput();
    output.systemMessage = message;
    return codec.toJson(output);
  }

  /**
   * Refuses a tool call before it runs and tells the agent why. Ward never answers {@code allow},
   * so allowed calls still go through Claude Code's own permission handling.
   */
  static String denyPreToolUse(final JsonCodec codec, final String reason) {
    final PreToolUseOutput output = new PreToolUseOutput();
    output.hookSpecificOutput = new PreToolUseDecision();
    output.hookSpecificOutput.hookEventName = "PreToolUse";
    output.hookSpecificOutput.permissionDecision = "deny";
    output.hookSpecificOutput.permissionDecisionReason = reason;
    return codec.toJson(output);
  }

  @JsonType
  public static final class PreToolUseOutput {
    public PreToolUseDecision hookSpecificOutput;
  }

  @JsonType
  public static final class PreToolUseDecision {
    public String hookEventName;
    public String permissionDecision;
    public String permissionDecisionReason;
  }

  @JsonType
  public static final class StopBlockOutput {
    public String decision;
    public String reason;
  }

  @JsonType
  public static final class SystemMessageOutput {
    public String systemMessage;
  }
}
