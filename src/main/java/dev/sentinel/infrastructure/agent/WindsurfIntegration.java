package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Advisory native Windsurf/Cascade hooks integration contract. */
public final class WindsurfIntegration extends NativeAgentIntegration {
  public WindsurfIntegration() {
    super(
        new Definition(
            "windsurf",
            ".windsurf/hooks.json",
            ".windsurf/hooks/sentinel.sh",
            "post_write_code,post_run_command,post_cascade_response",
            Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK)));
  }
}
