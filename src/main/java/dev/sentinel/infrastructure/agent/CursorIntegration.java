package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native Cursor hooks integration contract. */
public final class CursorIntegration extends NativeAgentIntegration {
  public CursorIntegration() {
    super(
        new Definition(
            "cursor",
            ".cursor/hooks.json",
            ".cursor/hooks/sentinel.sh",
            "preToolUse,afterFileEdit,stop",
            Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)));
  }
}
