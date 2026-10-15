package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native Gemini CLI hooks integration contract. */
public final class GeminiIntegration extends NativeAgentIntegration {
  public GeminiIntegration() {
    super(
        new Definition(
            "gemini",
            ".gemini/settings.json",
            ".gemini/hooks/sentinel.sh",
            "BeforeTool,AfterTool,AfterAgent,SessionEnd",
            Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)));
  }
}
