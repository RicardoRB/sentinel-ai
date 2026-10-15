package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationHealth;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared ownership-safe installer for native harness hook configurations. */
public final class NativeAgentIntegration implements AgentIntegration {
  private static final String AMP = "amp";

  public record Definition(
      String id,
      String configFile,
      String hookFile,
      String events,
      Set<IntegrationCapability> capabilities) {}

  private final Definition definition;

  public NativeAgentIntegration(final Definition definition) {
    this.definition = definition;
  }

  @Override
  public String id() {
    return definition.id();
  }

  @Override
  public Set<IntegrationCapability> capabilities() {
    return definition.capabilities();
  }

  @Override
  public boolean installed(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    return owned(root.resolve(definition.configFile()), marker())
        && owned(root.resolve(definition.hookFile()), marker());
  }

  @Override
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

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path config = root.resolve(definition.configFile());
    final Path hook = root.resolve(definition.hookFile());
    try {
      final IntegrationResult conflict = firstConflict(config, hook);
      if (conflict != null) {
        return conflict.withCapabilities(capabilities());
      }
      if (request.remove()) {
        return remove(config, hook);
      }
      return install(config, hook, request.guard());
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Could not update " + definition.id() + " integration: " + exception.getMessage(),
          exception);
    }
  }

  private IntegrationResult install(final Path config, final Path hook, final GuardMode guardMode)
      throws IOException {
    final String hookContent = hookContent(guardMode);
    final String configContent = configContent();
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
      makeExecutable(hook);
    }
    return IntegrationArtifacts.combine(
            results,
            "Sentinel " + id() + " integration is already present.",
            "Created Sentinel " + id() + " native hook integration.")
        .withCapabilities(capabilities());
  }

  private IntegrationResult remove(final Path config, final Path hook) throws IOException {
    if (!Files.exists(config) && !Files.exists(hook)) {
      return new IntegrationResult(
              IntegrationResult.Status.NOT_FOUND,
              List.of(),
              "No Sentinel " + id() + " integration found.")
          .withCapabilities(capabilities());
    }
    final List<String> removed = new ArrayList<>();
    final IntegrationResult configResult = IntegrationArtifacts.remove(config, marker(), "");
    if (configResult.status() == IntegrationResult.Status.CONFLICT) {
      return configResult.withCapabilities(capabilities());
    }
    removed.addAll(configResult.changed());
    final IntegrationResult hookResult = IntegrationArtifacts.remove(hook, marker(), "");
    if (hookResult.status() == IntegrationResult.Status.CONFLICT) {
      return hookResult.withCapabilities(capabilities());
    }
    removed.addAll(hookResult.changed());
    return new IntegrationResult(
            IntegrationResult.Status.REMOVED, removed, "Removed Sentinel " + id() + " integration.")
        .withCapabilities(capabilities());
  }

  private IntegrationResult firstConflict(final Path config, final Path hook) throws IOException {
    final IntegrationResult configConflict = IntegrationArtifacts.preflight(config, marker());
    return configConflict == null ? IntegrationArtifacts.preflight(hook, marker()) : configConflict;
  }

  private String configContent() {
    if (AMP.equals(definition.id())) {
      return "// "
          + marker()
          + "\n"
          + "export default function (amp) {\n"
          + "  amp.on('tool.call', async () => ({ action: 'allow' }));\n"
          + "  amp.on('tool.result', async () => undefined);\n"
          + "  amp.on('agent.end', async () => undefined);\n"
          + "}\n";
    }
    final StringBuilder content =
        new StringBuilder(256)
            .append("{\n  \"sentinelOwnership\": \"")
            .append(marker())
            .append("\",\n  \"hooks\": {");
    final String[] events = definition.events().split(",");
    for (int index = 0; index < events.length; index++) {
      if (index > 0) {
        content.append(',');
      }
      final String event = events[index];
      content.append("\n    \"").append(event).append("\": ");
      if (Set.of("PreToolUse", "BeforeTool").contains(event)) {
        content
            .append("[{\"matcher\":\".*\",\"hooks\":[{\"type\":\"command\",\"command\":\"")
            .append(definition.hookFile())
            .append("\"}]}]");
      } else {
        content
            .append("[{\"type\":\"command\",\"command\":\"")
            .append(definition.hookFile())
            .append("\"}]");
      }
    }
    return content.append("\n  }\n}\n").toString();
  }

  private String hookContent(final GuardMode guardMode) {
    return "#!/bin/sh\n"
        + "# "
        + marker()
        + "\n"
        + "# Native event adapter for "
        + definition.id()
        + ".\n"
        + "if [ \""
        + guardMode.name().toLowerCase(Locale.ROOT)
        + "\" = \"enable\" ]; then\n"
        + "  exec sentinel hook native --agent "
        + definition.id()
        + " --guard-approved-specs --event-stdin\n"
        + "fi\n"
        + "exec sentinel hook native --agent "
        + definition.id()
        + " --event-stdin\n";
  }

  private String marker() {
    return "sentinel-owned: " + definition.id() + "-native-hooks v1";
  }

  private static void makeExecutable(final Path file) throws IOException {
    try {
      Files.setPosixFilePermissions(
          file,
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      file.toFile().setExecutable(true, false);
    }
  }

  private static boolean owned(final Path target, final String marker) {
    try {
      return Files.exists(target) && Files.readString(target).contains(marker);
    } catch (IOException exception) {
      return false;
    }
  }
}
