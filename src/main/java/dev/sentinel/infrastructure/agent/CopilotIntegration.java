package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native GitHub Copilot CLI hooks integration contract. */
public final class CopilotIntegration extends NativeAgentIntegration {
  public CopilotIntegration() {
    super(
        new Definition(
            "copilot",
            ".github/hooks/sentinel.json",
            ".github/hooks/sentinel.sh",
            "preToolUse,postToolUse,sessionEnd",
            Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)));
  }
}
