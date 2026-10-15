package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Locale;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;

/** Advisory native Factory Droid plugin integration contract. */
public final class FactoryDroidIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "factory-droid",
          ".factory/plugins/sentinel.json",
          ".factory/plugins/sentinel.sh",
          "PostToolUse,Stop,SessionEnd",
          Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK));
  private final NativeIntegrationSupport support = new NativeIntegrationSupport();

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  @Override
  public NativeIntegrationSupport support() {
    return support;
  }

  @Override
  public String configContent() {
    return configuration();
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return hook(guardMode);
  }

  private static String configuration() {
    return "{\n  \"sentinelOwnership\": \""
        + marker()
        + "\",\n  \"hooks\": {\n    \"postToolUse\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}],\n    \"sessionEnd\": [{\"type\":\"command\",\"command\":\""
        + DEFINITION.hookFile()
        + "\"}]\n  }\n}\n";
  }

  private static String hook(final GuardMode guardMode) {
    return "#!/bin/sh\n# "
        + marker()
        + "\n# Native event adapter for factory-droid (guard mode: "
        + guardMode.name().toLowerCase(Locale.ROOT)
        + ").\nexec sentinel hook native --agent factory-droid --event-stdin\n";
  }

  private static String marker() {
    return "sentinel-owned: factory-droid-native-hooks v1";
  }

  @Override
  public String denyResponse(final String reason) {
    return "{\"decision\":\"deny\",\"reason\":" + quote(reason) + "}";
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
    final Payload value = codec.fromJson(payload, Payload.class);
    return value.input();
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return Set.of("sessionEnd", "SessionEnd").contains(input.eventName());
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

  private static String quote(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
