package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Advisory native Factory Droid plugin integration contract. */
public final class FactoryDroidIntegration extends NativeAgentIntegration {
  public FactoryDroidIntegration() {
    super(
        new Definition(
            "factory-droid",
            ".factory/plugins/sentinel.json",
            ".factory/plugins/sentinel.sh",
            "PostToolUse,Stop,SessionEnd",
            Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK)));
  }
}
