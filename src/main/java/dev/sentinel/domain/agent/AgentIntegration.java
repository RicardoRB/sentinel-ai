package dev.sentinel.domain.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface AgentIntegration extends AgentAdapter {
  IntegrationResult integrate(Path projectRoot, boolean remove);

  /**
   * Describes, without writing, every file that installing or refreshing this integration with
   * {@code guard} would create, modify, or remove, including user-owned conflicts.
   */
  List<PlannedFileChange> plan(Path projectRoot, GuardMode guard);

  default Set<IntegrationCapability> capabilities() {
    return Set.of();
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
