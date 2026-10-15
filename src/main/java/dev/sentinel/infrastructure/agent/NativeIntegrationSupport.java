package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationHealth;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared ownership-safe filesystem mechanics for project-local native integrations. */
public final class NativeIntegrationSupport {
  private final NativeAgentIntegration.Definition definition;

  public NativeIntegrationSupport(final NativeAgentIntegration.Definition definition) {
    this.definition = definition;
  }

  public boolean installed(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    return owned(root.resolve(definition.configFile()), marker())
        && owned(root.resolve(definition.hookFile()), marker());
  }

  public IntegrationHealth health(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path config = root.resolve(definition.configFile());
    final Path hook = root.resolve(definition.hookFile());
    final boolean configExists = Files.exists(config);
    final boolean hookExists = Files.exists(hook);
    final boolean configOwned = owned(config, marker());
    final boolean hookOwned = owned(hook, marker());
    if (!configExists && !hookExists) {
      return new IntegrationHealth(
          IntegrationHealth.Status.MISSING, "Integration is not configured.");
    }
    if ((configExists && !configOwned) || (hookExists && !hookOwned)) {
      return new IntegrationHealth(
          IntegrationHealth.Status.CONFLICT, "User-owned integration content requires review.");
    }
    if (configOwned && hookOwned) {
      return new IntegrationHealth(IntegrationHealth.Status.INSTALLED, "Integration is installed.");
    }
    return new IntegrationHealth(
        IntegrationHealth.Status.PARTIAL, "Integration is partially installed; re-run integrate.");
  }

  public IntegrationResult integrate(
      final Path projectRoot,
      final IntegrationRequest request,
      final String configContent,
      final String hookContent) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path config = root.resolve(definition.configFile());
    final Path hook = root.resolve(definition.hookFile());
    final NativeIntegrationTransaction transaction;
    try {
      transaction = NativeIntegrationTransaction.capture(config, hook);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not snapshot " + definition.id() + " integration: " + exception.getMessage(),
          exception);
    }
    try {
      final IntegrationResult conflict = firstConflict(config, hook);
      if (conflict != null) {
        return conflict.withCapabilities(definition.capabilities());
      }
      if (request.remove()) {
        return remove(config, hook);
      }
      return install(config, hook, configContent, hookContent);
    } catch (IOException exception) {
      try {
        transaction.rollback();
      } catch (IOException rollbackException) {
        exception.addSuppressed(rollbackException);
      }
      throw new UncheckedIOException(
          "Could not update " + definition.id() + " integration: " + exception.getMessage(),
          exception);
    }
  }

  private IntegrationResult install(
      final Path config, final Path hook, final String configContent, final String hookContent)
      throws IOException {
    final List<Map.Entry<Path, IntegrationResult>> results =
        new ArrayList<>(
            List.of(
                Map.entry(
                    config,
                    IntegrationArtifacts.installOrRefresh(
                        config, marker(), configContent, "Updated Sentinel " + id() + " hooks.")),
                Map.entry(
                    hook,
                    IntegrationArtifacts.installOrRefresh(
                        hook, marker(), hookContent, "Updated Sentinel " + id() + " hook."))));
    if (Files.exists(hook)) {
      IntegrationArtifacts.makeExecutable(hook);
    }
    return IntegrationArtifacts.combine(
            results,
            "Sentinel " + id() + " integration is already present.",
            "Created Sentinel " + id() + " native hook integration.")
        .withCapabilities(definition.capabilities());
  }

  private IntegrationResult remove(final Path config, final Path hook) throws IOException {
    if (!Files.exists(config) && !Files.exists(hook)) {
      return new IntegrationResult(
              IntegrationResult.Status.NOT_FOUND,
              List.of(),
              "No Sentinel " + id() + " integration found.")
          .withCapabilities(definition.capabilities());
    }
    final List<String> removed = new ArrayList<>();
    final IntegrationResult configResult = IntegrationArtifacts.remove(config, marker(), "");
    if (configResult.status() == IntegrationResult.Status.CONFLICT) {
      return configResult.withCapabilities(definition.capabilities());
    }
    removed.addAll(configResult.changed());
    final IntegrationResult hookResult = IntegrationArtifacts.remove(hook, marker(), "");
    if (hookResult.status() == IntegrationResult.Status.CONFLICT) {
      return hookResult.withCapabilities(definition.capabilities());
    }
    removed.addAll(hookResult.changed());
    return new IntegrationResult(
            IntegrationResult.Status.REMOVED, removed, "Removed Sentinel " + id() + " integration.")
        .withCapabilities(definition.capabilities());
  }

  private IntegrationResult firstConflict(final Path config, final Path hook) throws IOException {
    final IntegrationResult configConflict = IntegrationArtifacts.preflight(config, marker());
    return configConflict == null ? IntegrationArtifacts.preflight(hook, marker()) : configConflict;
  }

  private String id() {
    return definition.id();
  }

  private String marker() {
    return "sentinel-owned: " + definition.id() + "-native-hooks v1";
  }

  private static boolean owned(final Path target, final String marker) {
    try {
      return IntegrationArtifacts.owned(target, marker);
    } catch (IOException exception) {
      return false;
    }
  }
}
