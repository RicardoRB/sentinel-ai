package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Advisory native Windsurf/Cascade hooks integration contract. */
public final class WindsurfIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "windsurf",
          ".windsurf/hooks.json",
          ".windsurf/hooks/sentinel.sh",
          "post_write_code,post_run_command,post_cascade_response",
          Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK));
  private final NativeIntegrationSupport support = new NativeIntegrationSupport(DEFINITION);

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
    return NativeIntegrationTemplates.jsonConfiguration(DEFINITION);
  }

  @Override
  public String denyResponse(final String reason) {
    return "{\"decision\":\"deny\",\"reason\":" + quote(reason) + "}";
  }

  private static String quote(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
