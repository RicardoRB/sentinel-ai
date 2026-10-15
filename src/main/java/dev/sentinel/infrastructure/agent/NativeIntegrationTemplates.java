package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import java.util.Locale;
import java.util.Set;

/** Protocol-neutral templates used by concrete native harness adapters. */
final class NativeIntegrationTemplates {
  private NativeIntegrationTemplates() {}

  static String jsonConfiguration(final NativeAgentIntegration.Definition definition) {
    final String marker = marker(definition);
    final StringBuilder content =
        new StringBuilder(256)
            .append("{\n  \"sentinelOwnership\": \"")
            .append(marker)
            .append("\",\n  \"hooks\": {");
    final String[] events = definition.events().split(",");
    for (int index = 0; index < events.length; index++) {
      if (index > 0) {
        content.append(',');
      }
      final String event = events[index];
      content.append("\n    \"").append(event).append("\": ");
      final String hook = definition.hookFile();
      if (Set.of("PreToolUse", "BeforeTool").contains(event)) {
        content
            .append("[{\"matcher\":\".*\",\"hooks\":[{\"type\":\"command\",\"command\":\"")
            .append(hook)
            .append("\"}]}]");
      } else {
        content.append("[{\"type\":\"command\",\"command\":\"").append(hook).append("\"}]");
      }
    }
    return content.append("\n  }\n}\n").toString();
  }

  static String shellHook(
      final NativeAgentIntegration.Definition definition, final GuardMode guardMode) {
    return "#!/bin/sh\n# "
        + marker(definition)
        + "\n# Native event adapter for "
        + definition.id()
        + ".\nif [ \""
        + guardMode.name().toLowerCase(Locale.ROOT)
        + "\" = \"enable\" ]; then\n  exec sentinel hook native --agent "
        + definition.id()
        + " --guard-approved-specs --event-stdin\nfi\nexec sentinel hook native --agent "
        + definition.id()
        + " --event-stdin\n";
  }

  static String marker(final NativeAgentIntegration.Definition definition) {
    return "sentinel-owned: " + definition.id() + "-native-hooks v1";
  }
}
