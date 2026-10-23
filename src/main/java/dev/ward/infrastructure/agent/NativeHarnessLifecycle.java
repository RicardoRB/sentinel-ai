package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import java.nio.file.Path;
import java.util.List;

/** Installs, refreshes, removes and diagnoses a native harness's Ward-owned files. */
public interface NativeHarnessLifecycle extends AgentIntegration {
  /** Returns the files installing under {@code root} with {@code guardMode} would write. */
  NativeArtifactPlan artifacts(Path root, GuardMode guardMode);

  @Override
  default boolean installed(final Path projectRoot) {
    return NativeIntegrationSupport.installed(projectRoot, artifacts(projectRoot, GuardMode.KEEP));
  }

  /**
   * Native harnesses read one hook configuration in the repository. Its hook names no project, so
   * every nested project shares it and Ward picks the projects from the edited files.
   */
  @Override
  default boolean installsAtRepositoryRoot() {
    return true;
  }

  @Override
  default IntegrationHealth health(final Path root, final String projectPath) {
    return health(root);
  }

  @Override
  default IntegrationHealth health(final Path projectRoot) {
    return NativeIntegrationSupport.health(projectRoot, artifacts(projectRoot, GuardMode.KEEP));
  }

  @Override
  default List<PlannedFileChange> plan(final Path projectRoot, final GuardMode guard) {
    return NativeArtifactPlans.plannedChanges(artifacts(projectRoot, guard));
  }

  @Override
  default IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  default IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    return NativeIntegrationSupport.integrate(
        projectRoot, request, artifacts(projectRoot, request.guard()));
  }
}
