package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Native OpenAI Codex CLI integration contract. */
public final class CodexIntegration extends NativeAgentIntegration {
  public CodexIntegration() {
    super(
        new Definition(
            "codex",
            ".codex/hooks.json",
            ".codex/hooks/sentinel.sh",
            "PreToolUse,PostToolUse",
            Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)));
  }
}
