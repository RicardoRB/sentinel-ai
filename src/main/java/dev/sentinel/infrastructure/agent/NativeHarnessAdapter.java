package dev.sentinel.infrastructure.agent;

import dev.sentinel.application.agent.NativeHookProtocol;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationHealth;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Path;
import java.util.Set;

/**
 * Shared lifecycle wiring for concrete harness adapters; protocol behavior stays in each adapter.
 */
public interface NativeHarnessAdapter extends AgentIntegration, NativeHookProtocol {
  NativeAgentIntegration.Definition definition();

  String configContent();

  default NativeArtifactPlan artifactPlan(final Path projectRoot, final GuardMode guardMode) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path hook = root.resolve(definition().hookFile());
    return new NativeArtifactPlan(
        id(),
        root.resolve(definition().configFile()),
        hook,
        NativeHookText.marker(id()),
        configContent(),
        hookContent(NativeIntegrationSupport.resolveGuard(root, definition(), guardMode)),
        capabilities());
  }

  String hookContent(GuardMode guardMode);

  @Override
  default String id() {
    return definition().id();
  }

  @Override
  default Set<IntegrationCapability> capabilities() {
    return definition().capabilities();
  }

  @Override
  default boolean installed(final Path projectRoot) {
    return NativeIntegrationSupport.installed(
        projectRoot, artifactPlan(projectRoot, GuardMode.KEEP));
  }

  @Override
  default IntegrationHealth health(final Path projectRoot) {
    return NativeIntegrationSupport.health(projectRoot, artifactPlan(projectRoot, GuardMode.KEEP));
  }

  @Override
  default IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  default IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    return NativeIntegrationSupport.integrate(
        projectRoot, request, artifactPlan(projectRoot, request.guard()));
  }
}
