package dev.sentinel.domain.agent;

import java.nio.file.Path;
import java.util.Set;

public interface AgentIntegration extends AgentAdapter {
  IntegrationResult integrate(Path projectRoot, boolean remove);

  default Set<IntegrationCapability> capabilities() {
    return Set.of();
  }

  /** Serializes a normalized native-hook denial for this agent's protocol. */
  default String denyResponse(final String reason) {
    return "{\"decision\":\"deny\",\"reason\":\""
        + reason.replace("\\", "\\\\").replace("\"", "\\\"")
        + "\"}";
  }

  default boolean installed(final Path projectRoot) {
    return false;
  }

  default IntegrationHealth health(final Path projectRoot) {
    return installed(projectRoot)
        ? new IntegrationHealth(IntegrationHealth.Status.INSTALLED, "Integration is installed.")
        : new IntegrationHealth(IntegrationHealth.Status.MISSING, "Integration is not configured.");
  }

  /**
   * Installs, refreshes, or removes the integration as requested. Integrations without an
   * approved-artifact guard ignore the guard mode.
   */
  default IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    return integrate(projectRoot, request.remove());
  }
}
