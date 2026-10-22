package dev.ward.domain.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** An agent integration that writes project-local hook and instruction files. */
public interface AgentIntegration extends AgentAdapter {
  /**
   * Installs or refreshes the integration under {@code projectRoot}, or removes its Ward-owned
   * files when {@code remove}.
   */
  IntegrationResult integrate(Path projectRoot, boolean remove);

  /**
   * Describes, without writing, every file that installing or refreshing this integration with
   * {@code guard} would create, modify, or remove, including user-owned conflicts.
   */
  List<PlannedFileChange> plan(Path projectRoot, GuardMode guard);

  /**
   * Describes, without writing, what {@code request} would change under {@code root}. Integrations
   * that do not install nested projects at the repository root ignore the project path.
   */
  default List<PlannedFileChange> plan(final Path root, final IntegrationRequest request) {
    return plan(root, request.guard());
  }

  /**
   * Returns whether a nested Ward project is installed at the repository root, where agent sessions
   * start, rather than in the project directory; the default is false.
   */
  default boolean installsAtRepositoryRoot() {
    return false;
  }

  /** Returns what the installed integration enforces; the default is nothing. */
  default Set<IntegrationCapability> capabilities() {
    return Set.of();
  }

  /**
   * Returns whether the integration is installed under {@code projectRoot}; the default is false.
   */
  default boolean installed(final Path projectRoot) {
    return false;
  }

  /** Reports whether the integration is installed, for {@code ward doctor}. */
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
