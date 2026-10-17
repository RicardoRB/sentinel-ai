package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/** Builds a native adapter's artifact plan and the file changes installing it would make. */
final class NativeArtifactPlans {
  private NativeArtifactPlans() {}

  /** Builds the files an adapter would write for {@code guardMode}. */
  static NativeArtifactPlan of(
      final NativeHarnessAdapter adapter, final Path projectRoot, final GuardMode guardMode) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final NativeAgentIntegration.Definition definition = adapter.definition();
    return new NativeArtifactPlan(
        adapter.id(),
        root.resolve(definition.configFile()),
        root.resolve(definition.hookFile()),
        NativeHookText.marker(adapter.id()),
        adapter.configContent(),
        adapter.hookContent(NativeIntegrationSupport.resolveGuard(root, definition, guardMode)),
        adapter.capabilities());
  }

  /** Plans an install with the same ownership decision {@link #integrate} writes with. */
  static List<PlannedFileChange> plannedChanges(final NativeArtifactPlan plan) {
    try {
      return List.of(
          new PlannedFileChange(
              plan.configPath(),
              IntegrationArtifacts.decide(plan.configPath(), plan.marker(), plan.configContent())),
          new PlannedFileChange(
              plan.hookPath(),
              IntegrationArtifacts.decide(plan.hookPath(), plan.marker(), plan.hookContent())));
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not plan " + plan.id() + " integration: " + exception.getMessage(), exception);
    }
  }
}
