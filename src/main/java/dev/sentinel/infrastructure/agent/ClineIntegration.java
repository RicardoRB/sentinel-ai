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

/** Native Cline hooks integration contract. */
public final class ClineIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "cline",
          ".cline/hooks/sentinel.json",
          ".cline/hooks/sentinel.sh",
          "beforeTool,afterTool,turn_end",
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
        + "\",\n  \"hooks\": {\n    \"PreToolUse\": [{\"matcher\":\".*\",\"hooks\":[{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}]}],\n    \"PostToolUse\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}],\n    \"TaskCompleted\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}]\n  }\n}\n";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return "#!/bin/sh\n# "
        + NativeHookText.marker(DEFINITION.id())
        + "\n# Native event adapter for cline.\nif [ \""
        + guardMode.name().toLowerCase(Locale.ROOT)
        + "\" = \"enable\" ]; then\n  exec sentinel hook native --agent cline --guard-approved-specs --event-stdin\nfi\nexec sentinel hook native --agent cline --event-stdin\n";
  }

  public String denyResponse(final String reason) {
    return NativeHookText.genericDenial(reason);
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
    return NativeHookText.eventIn(input, Set.of("TaskCompleted", "turn_end"));
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
