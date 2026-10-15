package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationHealth;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Base contract for native harness adapters and compatibility support for generic adapters. */
public class NativeAgentIntegration implements AgentIntegration {
  private final Definition definition;
  private final NativeIntegrationSupport support;

  public record Definition(
      String id,
      String configFile,
      String hookFile,
      String events,
      Set<IntegrationCapability> capabilities) {}

  public NativeAgentIntegration(final Definition definition) {
    this.definition = definition;
    this.support = new NativeIntegrationSupport(definition);
  }

  @Override
  public String id() {
    return definition.id();
  }

  @Override
  public Set<IntegrationCapability> capabilities() {
    return definition.capabilities();
  }

  @Override
  public boolean installed(final Path projectRoot) {
    return support.installed(projectRoot);
  }

  @Override
  public IntegrationHealth health(final Path projectRoot) {
    return support.health(projectRoot);
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    return support.integrate(projectRoot, request, configContent(), hookContent(request.guard()));
  }

  protected String configContent() {
    final StringBuilder content =
        new StringBuilder(256)
            .append("{\n  \"sentinelOwnership\": \"")
            .append(marker())
            .append("\",\n  \"hooks\": {");
    final String[] events = definition.events().split(",");
    for (int index = 0; index < events.length; index++) {
      if (index > 0) {
        content.append(',');
      }
      final String event = events[index];
      content.append("\n    \"").append(event).append("\": ");
      if (Set.of("PreToolUse", "BeforeTool").contains(event)) {
        content
            .append("[{\"matcher\":\".*\",\"hooks\":[{\"type\":\"command\",\"command\":\"")
            .append(definition.hookFile())
            .append("\"}]}]");
      } else {
        content
            .append("[{\"type\":\"command\",\"command\":\"")
            .append(definition.hookFile())
            .append("\"}]");
      }
    }
    return content.append("\n  }\n}\n").toString();
  }

  protected String hookContent(final GuardMode guardMode) {
    return "#!/bin/sh\n"
        + "# "
        + marker()
        + "\n"
        + "# Native event adapter for "
        + definition.id()
        + ".\n"
        + "if [ \""
        + guardMode.name().toLowerCase(Locale.ROOT)
        + "\" = \"enable\" ]; then\n"
        + "  exec sentinel hook native --agent "
        + definition.id()
        + " --guard-approved-specs --event-stdin\n"
        + "fi\n"
        + "exec sentinel hook native --agent "
        + definition.id()
        + " --event-stdin\n";
  }

  protected final String marker() {
    return "sentinel-owned: " + definition.id() + "-native-hooks v1";
  }
}
