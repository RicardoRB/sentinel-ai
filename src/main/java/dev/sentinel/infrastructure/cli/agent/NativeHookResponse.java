package dev.sentinel.infrastructure.cli.agent;

/** Serializes normalized Sentinel denials into native harness response envelopes. */
final class NativeHookResponse {
  private NativeHookResponse() {}

  static String deny(final String agent, final String reason) {
    return switch (agent) {
      case "codex" ->
          "{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\",\"permissionDecision\":\"deny\",\"permissionDecisionReason\":"
              + quote(reason)
              + "}}";
      case "cursor" ->
          "{\"permission\":\"deny\",\"user_message\":"
              + quote(reason)
              + ",\"agent_message\":"
              + quote(reason)
              + "}";
      case "gemini" -> "{\"decision\":\"deny\",\"reason\":" + quote(reason) + "}";
      case "amp" -> "{\"action\":\"reject-and-continue\",\"message\":" + quote(reason) + "}";
      default -> "{\"decision\":\"deny\",\"reason\":" + quote(reason) + "}";
    };
  }

  private static String quote(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
