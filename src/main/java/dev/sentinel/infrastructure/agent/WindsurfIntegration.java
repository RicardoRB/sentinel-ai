package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Locale;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;

/** Advisory native Windsurf/Cascade hooks integration contract. */
public final class WindsurfIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "windsurf",
          ".windsurf/hooks.json",
          ".windsurf/hooks/sentinel.sh",
          "post_write_code,post_run_command,post_cascade_response",
          Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK));

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  @Override
  public String configContent() {
    return "{\n  \"sentinelOwnership\": \""
        + NativeHookText.marker(DEFINITION.id())
        + "\",\n  \"hooks\": {\n    \"post_write_code\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}],\n    \"post_run_command\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}],\n    \"post_cascade_response\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}]\n  }\n}\n";
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return "#!/bin/sh\n# "
        + NativeHookText.marker(DEFINITION.id())
        + "\n# Native event adapter for windsurf (guard mode: "
        + guardMode.name().toLowerCase(Locale.ROOT)
        + ").\nexec sentinel hook native --agent windsurf --event-stdin\n";
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
    return "post_cascade_response".equals(input.eventName());
  }

  private static final class Payload {
    String event;

    @JsonProperty("event_name")
    String eventNameValue;

    @JsonProperty("hook_event_name")
    String hookEventName;

    String type;

    @JsonProperty("tool_name")
    String toolNameValue;

    String cwd;

    @JsonProperty("file_path")
    String filePath;

    String path;
    String command;
    String patch;
    String patchText;
    String skill;
    String name;

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
