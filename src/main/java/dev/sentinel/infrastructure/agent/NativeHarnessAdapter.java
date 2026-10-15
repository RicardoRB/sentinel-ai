package dev.sentinel.infrastructure.agent;

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
public interface NativeHarnessAdapter extends AgentIntegration {
  NativeAgentIntegration.Definition definition();

  NativeIntegrationSupport support();

  String configContent();

  default String hookContent(final GuardMode guardMode) {
    return NativeIntegrationTemplates.shellHook(definition(), guardMode);
  }

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
    return support().installed(projectRoot);
  }

  @Override
  default IntegrationHealth health(final Path projectRoot) {
    return support().health(projectRoot);
  }

  @Override
  default IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  default IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    return support().integrate(projectRoot, request, configContent(), hookContent(request.guard()));
  }
}
