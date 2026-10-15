package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Metadata shared by concrete native harness adapters. */
public final class NativeAgentIntegration {
  private NativeAgentIntegration() {}

  public record Definition(
      String id,
      String configFile,
      String hookFile,
      String events,
      Set<IntegrationCapability> capabilities) {
    public Definition {
      capabilities = Set.copyOf(capabilities);
    }
  }
}
