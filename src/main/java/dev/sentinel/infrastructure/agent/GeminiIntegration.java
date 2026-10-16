package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Locale;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/** Native Gemini CLI hooks integration contract. */
public final class GeminiIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "gemini",
          ".gemini/settings.json",
          ".gemini/hooks/sentinel.sh",
          "BeforeTool,AfterTool,AfterAgent,SessionEnd",
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

  @Override
  public String configContent() {
    return "{\n  \"sentinelOwnership\": \""
        + NativeHookText.marker(DEFINITION.id())
        + "\",\n  \"hooks\": {\n    \"BeforeTool\": [{\"matcher\":\".*\",\"hooks\":[{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}]}],\n    \"AfterTool\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}],\n    \"AfterAgent\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}],\n    \"SessionEnd\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}]\n  }\n}\n";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return "#!/bin/sh\n# "
        + NativeHookText.marker(DEFINITION.id())
        + "\n# Native event adapter for gemini.\nif [ \""
        + guardMode.name().toLowerCase(Locale.ROOT)
        + "\" = \"enable\" ]; then\n  exec sentinel hook native --agent gemini --guard-approved-specs --event-stdin\nfi\nexec sentinel hook native --agent gemini --event-stdin\n";
  }

  public String denyResponse(final String reason) {
    return "{\"decision\":\"deny\",\"reason\":" + NativeHookText.quote(reason) + "}";
  }

  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY -> denyResponse(outcome.message());
      case FEEDBACK, ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return NativeHookText.eventIn(input, Set.of("AfterAgent", "SessionEnd"));
  }

  @JsonType
  public static final class Payload {
    public String event;

    @JsonProperty("event_name")
    public String eventNameValue;

    @JsonProperty("hook_event_name")
    public String hookEventName;

    public String type;

    @JsonProperty("tool_name")
    public String toolNameValue;

    public String cwd;

    @JsonProperty("file_path")
    public String filePath;

    public String path;
    public String command;
    public String patch;
    public String patchText;
    public String skill;
    public String name;

    NativeHookInput input() {
      return new NativeHookInput(
          first(event, eventNameValue, hookEventName, type),
          first(toolNameValue, name),
          cwd == null ? "" : cwd,
          first(filePath, path),
          command,
          first(patch, patchText),
          skill,
          false);
    }

    private static String first(final String... values) {
      for (final String value : values) {
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
      return null;
    }
  }
}
