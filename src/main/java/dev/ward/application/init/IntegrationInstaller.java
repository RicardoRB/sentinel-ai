package dev.ward.application.init;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.SupportedAgents;
import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Validates, installs, and rolls back the agent integrations selected during init. */
final class IntegrationInstaller {
  private final Set<AgentIntegration> integrations;
  private final InitSetupCatalog catalog;

  IntegrationInstaller(final Set<AgentIntegration> integrations, final InitSetupCatalog catalog) {
    this.integrations = Set.copyOf(integrations);
    this.catalog = catalog;
  }

  void validate(final List<String> ids) {
    if (ids.isEmpty()) {
      throw new WardException("Select at least one integration, or choose none.");
    }
    final boolean noIntegration = ids.contains(InitSetupCatalog.NO_INTEGRATION);
    if (noIntegration && ids.size() > 1) {
      throw new WardException(
          "The no-integration choice cannot be combined with another integration.");
    }
    ids.forEach(catalog::integration);
  }

  /**
   * Installs each selected integration, recording every result in {@code installed} so a failure
   * can be rolled back.
   */
  void install(final Path root, final List<String> ids, final List<IntegrationResult> installed) {
    for (final String id : ids) {
      if (InitSetupCatalog.NO_INTEGRATION.equals(id)) {
        continue;
      }
      final IntegrationResult result = install(root, id);
      installed.add(result);
      if (result.status() == IntegrationResult.Status.CONFLICT) {
        throw new WardException(
            "Cannot initialize "
                + root
                + ": selected integration conflicts with user-owned content.");
      }
    }
  }

  /** Plans each selected integration's install, in selection order, without writing. */
  List<PlannedFileChange> plan(final Path root, final List<String> ids) {
    if (ids.stream().noneMatch(id -> !InitSetupCatalog.NO_INTEGRATION.equals(id))) {
      return List.of();
    }
    if (integrations.isEmpty()) {
      throw new WardException("Agent integrations are unavailable in this runtime.");
    }
    return ids.stream()
        .filter(id -> !InitSetupCatalog.NO_INTEGRATION.equals(id))
        .flatMap(id -> integration(id).plan(root, GuardMode.KEEP).stream())
        .toList();
  }

  // Cleanup is best effort: any failure here must not hide the original setup failure.
  void rollbackNew(final Path root, final List<String> ids, final List<IntegrationResult> results) {
    for (int i = 0; i < Math.min(ids.size(), results.size()); i++) {
      if (results.get(i).status() != IntegrationResult.Status.CHANGED) {
        continue;
      }
      rollbackOne(root, ids.get(i));
    }
  }

  private void rollbackOne(final Path root, final String id) {
    final FutureTask<IntegrationResult> rollback =
        new FutureTask<>(() -> integration(id).integrate(root, true));
    rollback.run();
    try {
      rollback.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException ignored) {
      // Preserve the original initialization error; ownership-safe removal remains available.
    }
  }

  private IntegrationResult install(final Path root, final String id) {
    if (integrations.isEmpty()) {
      throw new WardException("Agent integrations are unavailable in this runtime.");
    }
    final AgentIntegration adapter = integration(id);
    return adapter.integrate(root, false).withCapabilities(adapter.capabilities());
  }

  private AgentIntegration integration(final String id) {
    return integrations.stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new WardException(
                    "Unknown agent '" + id + "'. Supported agents: " + SupportedAgents.idsText()));
  }
}
