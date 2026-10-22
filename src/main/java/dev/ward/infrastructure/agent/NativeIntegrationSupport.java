package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shared ownership-safe filesystem mechanics for project-local native integrations. */
public final class NativeIntegrationSupport {
  /** The wrapper line that marks an enabled guard, in current and earlier wrappers. */
  private static final String GUARD_ENABLED = "\"enable\" = \"enable\" ]";

  private NativeIntegrationSupport() {}

  /** Returns whether both files of {@code plan} exist and carry the Ward ownership marker. */
  public static boolean installed(final Path projectRoot, final NativeArtifactPlan plan) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    return owned(root.resolve(plan.configPath()), plan.marker())
        && owned(root.resolve(plan.hookPath()), plan.marker());
  }

  /** Reports whether the files of {@code plan} are installed, missing, partial, or user-owned. */
  public static IntegrationHealth health(final Path projectRoot, final NativeArtifactPlan plan) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path config = root.resolve(plan.configPath());
    final Path hook = root.resolve(plan.hookPath());
    final boolean configExists = Files.exists(config);
    final boolean hookExists = Files.exists(hook);
    final boolean configOwned = owned(config, plan.marker());
    final boolean hookOwned = owned(hook, plan.marker());
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

  /**
   * Writes or removes the files of {@code plan} as {@code request} says, never overwriting
   * user-owned files.
   */
  public static IntegrationResult integrate(
      final Path projectRoot, final IntegrationRequest request, final NativeArtifactPlan plan) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path config = root.resolve(plan.configPath());
    final Path hook = root.resolve(plan.hookPath());
    final NativeIntegrationTransaction transaction;
    try {
      transaction = NativeIntegrationTransaction.capture(config, hook);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not snapshot " + plan.id() + " integration: " + exception.getMessage(), exception);
    }
    try {
      final IntegrationResult conflict = firstConflict(config, hook, plan.marker());
      if (conflict != null) {
        return conflict.withCapabilities(plan.capabilities());
      }
      if (request.remove()) {
        return remove(plan);
      }
      return install(plan);
    } catch (IOException exception) {
      try {
        transaction.rollback();
      } catch (IOException rollbackException) {
        exception.addSuppressed(rollbackException);
      }
      throw new UncheckedIOException(
          "Could not update " + plan.id() + " integration: " + exception.getMessage(), exception);
    }
  }

  /**
   * Resolves {@link GuardMode#KEEP} to the state of the installed hook wrapper, so a refresh
   * without a guard option neither enables nor silently disables the guard.
   */
  static GuardMode resolveGuard(
      final Path root,
      final NativeAgentIntegration.Definition definition,
      final GuardMode requested) {
    final Path current = root.resolve(definition.hookFile());
    return resolveGuard(
        Files.exists(current) ? current : root.resolve(definition.priorHookFile()), requested);
  }

  static GuardMode resolveGuard(final Path hook, final GuardMode requested) {
    if (requested != GuardMode.KEEP) {
      return requested;
    }
    try {
      return Files.isRegularFile(hook) && Files.readString(hook).contains(GUARD_ENABLED)
          ? GuardMode.ENABLE
          : GuardMode.DISABLE;
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not read " + hook + ": " + exception.getMessage(), exception);
    }
  }

  private static IntegrationResult install(final NativeArtifactPlan plan) throws IOException {
    final Path config = plan.configPath();
    final Path hook = plan.hookPath();
    final List<Map.Entry<Path, IntegrationResult>> results =
        new ArrayList<>(
            List.of(
                Map.entry(
                    config,
                    IntegrationArtifacts.installOrRefresh(
                        config,
                        plan.marker(),
                        plan.configContent(),
                        "Updated Ward " + plan.id() + " hooks.")),
                Map.entry(
                    hook,
                    IntegrationArtifacts.installOrRefresh(
                        hook,
                        plan.marker(),
                        plan.hookContent(),
                        "Updated Ward " + plan.id() + " hook."))));
    if (Files.exists(hook)) {
      IntegrationArtifacts.makeExecutable(hook);
    }
    return IntegrationArtifacts.combine(
            results,
            "Ward " + plan.id() + " integration is already present.",
            "Created Ward " + plan.id() + " native hook integration.")
        .withCapabilities(plan.capabilities());
  }

  private static IntegrationResult remove(final NativeArtifactPlan plan) throws IOException {
    final Path config = plan.configPath();
    final Path hook = plan.hookPath();
    if (!Files.exists(config) && !Files.exists(hook)) {
      return new IntegrationResult(
              IntegrationResult.Status.NOT_FOUND,
              List.of(),
              "No Ward " + plan.id() + " integration found.")
          .withCapabilities(plan.capabilities());
    }
    final List<String> removed = new ArrayList<>();
    final IntegrationResult configResult = IntegrationArtifacts.remove(config, plan.marker(), "");
    if (configResult.status() == IntegrationResult.Status.CONFLICT) {
      return configResult.withCapabilities(plan.capabilities());
    }
    removed.addAll(configResult.changed());
    final IntegrationResult hookResult = IntegrationArtifacts.remove(hook, plan.marker(), "");
    if (hookResult.status() == IntegrationResult.Status.CONFLICT) {
      return hookResult.withCapabilities(plan.capabilities());
    }
    removed.addAll(hookResult.changed());
    return new IntegrationResult(
            IntegrationResult.Status.REMOVED,
            removed,
            "Removed Ward " + plan.id() + " integration.")
        .withCapabilities(plan.capabilities());
  }

  private static IntegrationResult firstConflict(
      final Path config, final Path hook, final String marker) throws IOException {
    final IntegrationResult configConflict = IntegrationArtifacts.preflight(config, marker);
    return configConflict == null ? IntegrationArtifacts.preflight(hook, marker) : configConflict;
  }

  private static boolean owned(final Path target, final String marker) {
    try {
      return IntegrationArtifacts.owned(target, marker);
    } catch (IOException exception) {
      return false;
    }
  }
}
