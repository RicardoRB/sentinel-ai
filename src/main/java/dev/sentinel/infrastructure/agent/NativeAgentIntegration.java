package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationCapability;
import java.util.Set;

/** Metadata shared by concrete native harness adapters. */
public interface NativeAgentIntegration {

  /**
   * Describes a native harness: its id, generated files, hook events and capabilities. {@code
   * priorHookFile} is where an earlier Sentinel version wrote the wrapper; until the current one
   * exists, a refresh keeps the guard state found there.
   */
  record Definition(
      String id,
      String configFile,
      String hookFile,
      String events,
      Set<IntegrationCapability> capabilities,
      String priorHookFile) {
    public Definition {
      capabilities = Set.copyOf(capabilities);
    }

    public Definition(
        final String id,
        final String configFile,
        final String hookFile,
        final String events,
        final Set<IntegrationCapability> capabilities) {
      this(id, configFile, hookFile, events, capabilities, hookFile);
    }
  }
}
