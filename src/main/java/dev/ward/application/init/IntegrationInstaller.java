package dev.ward.application.init;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.SupportedAgents;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.Project;
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
  void install(
      final Project project, final List<String> ids, final List<IntegrationResult> installed) {
    for (final String id : ids) {
      if (InitSetupCatalog.NO_INTEGRATION.equals(id)) {
        continue;
      }
      final IntegrationResult result = install(project, id);
      installed.add(result);
      if (result.status() == IntegrationResult.Status.CONFLICT) {
        throw new WardException(
            "Cannot initialize "
                + project.root()
                + ": selected integration conflicts with user-owned content.");
      }
    }
  }

  /** Plans each selected integration's install, in selection order, without writing. */
  List<PlannedFileChange> plan(final Project project, final List<String> ids) {
    if (ids.stream().noneMatch(id -> !InitSetupCatalog.NO_INTEGRATION.equals(id))) {
      return List.of();
    }
    if (integrations.isEmpty()) {
      throw new WardException("Agent integrations are unavailable in this runtime.");
    }
    return ids.stream()
        .filter(id -> !InitSetupCatalog.NO_INTEGRATION.equals(id))
        .flatMap(
            id -> {
              final AgentIntegration adapter = integration(id);
              return adapter
                  .plan(
                      root(project, adapter),
                      request(project, adapter, new IntegrationRequest(false, GuardMode.KEEP)))
                  .stream();
            })
        .toList();
  }

  // Cleanup is best effort: any failure here must not hide the original setup failure.
  void rollbackNew(
      final Project project, final List<String> ids, final List<IntegrationResult> results) {
    for (int i = 0; i < Math.min(ids.size(), results.size()); i++) {
      if (results.get(i).status() != IntegrationResult.Status.CHANGED) {
        continue;
      }
      rollbackOne(project, ids.get(i));
    }
  }

  private void rollbackOne(final Project project, final String id) {
    final AgentIntegration adapter = integration(id);
    final FutureTask<IntegrationResult> rollback =
        new FutureTask<>(
            () ->
                adapter.integrate(
                    root(project, adapter), request(project, adapter, IntegrationRequest.REMOVE)));
    rollback.run();
    try {
      rollback.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException ignored) {
      // Preserve the original initialization error; ownership-safe removal remains available.
    }
  }

  private IntegrationResult install(final Project project, final String id) {
    if (integrations.isEmpty()) {
      throw new WardException("Agent integrations are unavailable in this runtime.");
    }
    final AgentIntegration adapter = integration(id);
    return adapter
        .integrate(root(project, adapter), request(project, adapter, IntegrationRequest.INSTALL))
        .withCapabilities(adapter.capabilities());
  }

  /**
   * Agent sessions start at the repository root, so nested projects install there when they can.
   */
  private static Path root(final Project project, final AgentIntegration adapter) {
    return project.installRoot(adapter.installsAtRepositoryRoot());
  }

  private static IntegrationRequest request(
      final Project project, final AgentIntegration adapter, final IntegrationRequest request) {
    return request.forProject(project.installPath(adapter.installsAtRepositoryRoot()));
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
