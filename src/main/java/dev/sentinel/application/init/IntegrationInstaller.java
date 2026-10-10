package dev.sentinel.application.init;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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
      throw new SentinelException("Select at least one integration, or choose none.");
    }
    final boolean noIntegration = ids.contains(InitSetupCatalog.NO_INTEGRATION);
    if (noIntegration && ids.size() > 1) {
      throw new SentinelException(
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
        throw new SentinelException(
            "Cannot initialize "
                + root
                + ": selected integration conflicts with user-owned content.");
      }
    }
  }

  // Cleanup is best effort: any failure here must not hide the original setup failure.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  void rollbackNew(final Path root, final List<String> ids, final List<IntegrationResult> results) {
    for (int i = 0; i < Math.min(ids.size(), results.size()); i++) {
      if (results.get(i).status() != IntegrationResult.Status.CHANGED) {
        continue;
      }
      try {
        integration(ids.get(i)).integrate(root, true);
      } catch (RuntimeException ignored) {
        // Preserve the original initialization error; ownership-safe removal remains available.
      }
    }
  }

  private IntegrationResult install(final Path root, final String id) {
    if (integrations.isEmpty()) {
      throw new SentinelException("Agent integrations are unavailable in this runtime.");
    }
    return integration(id).integrate(root, false);
  }

  private AgentIntegration integration(final String id) {
    return integrations.stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(
            () ->
                new SentinelException(
                    "Unknown agent '" + id + "'. Supported agents: opencode, claude-code"));
  }
}
