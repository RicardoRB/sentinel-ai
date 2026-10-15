package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Metadata shared by concrete native harness adapters. */
public interface NativeAgentIntegration {

  /** Describes a native harness: its id, generated files, hook events and capabilities. */
  record Definition(
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
