package dev.ward.infrastructure.agent;

import dev.ward.application.agent.NativeHookProtocol;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import java.nio.file.Path;
import java.util.Set;

/**
 * Shared lifecycle wiring for concrete harness adapters; protocol behavior stays in each adapter.
 */
public interface NativeHarnessAdapter extends NativeHarnessLifecycle, NativeHookProtocol {
  /** Returns the harness's ID, generated files, events, and capabilities. */
  NativeAgentIntegration.Definition definition();

  /** Returns the harness configuration file content that registers the hook. */
  String configContent();

  /** Returns the hook wrapper script, with the approved-artifact guard set by {@code guardMode}. */
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
  default NativeArtifactPlan artifacts(final Path root, final GuardMode guardMode) {
    return NativeArtifactPlans.of(this, root, guardMode);
  }
}
