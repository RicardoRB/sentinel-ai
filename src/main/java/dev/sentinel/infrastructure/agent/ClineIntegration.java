package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native Cline hooks integration contract. */
public final class ClineIntegration extends NativeAgentIntegration {
  public ClineIntegration() {
    super(
        new Definition(
            "cline",
            ".cline/hooks/sentinel.json",
            ".cline/hooks/sentinel.sh",
            "beforeTool,afterTool,turn_end",
            Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)));
  }
}
