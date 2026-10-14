package dev.sentinel.domain.agent;

import java.nio.file.Path;

public interface AgentIntegration extends AgentAdapter {
  IntegrationResult integrate(Path projectRoot, boolean remove);

  /**
   * Installs, refreshes, or removes the integration as requested. Integrations without an
   * approved-artifact guard ignore the guard mode.
   */
  default IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    return integrate(projectRoot, request.remove());
  }
}
