package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native GitHub Copilot CLI hooks integration contract. */
public final class CopilotIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "copilot",
          ".github/hooks/sentinel.json",
          ".github/hooks/sentinel.sh",
          "preToolUse,postToolUse,sessionEnd",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.POST_EDIT,
              IntegrationCapability.TURN_END,
              IntegrationCapability.FEEDBACK,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD));
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
