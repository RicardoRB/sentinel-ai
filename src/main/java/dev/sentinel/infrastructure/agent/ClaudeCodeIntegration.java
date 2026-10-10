package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Ownership-safe Claude Code project hook integration. */
public final class ClaudeCodeIntegration implements AgentIntegration {
  public static final String SETTINGS_MARKER = "sentinel-owned: claude-code-edit-write v1";
  public static final String HOOK_MARKER = "sentinel-owned: edit-write-guard v1";
  private static final String SETTINGS_FILE = ".claude/settings.json";
  private static final String HOOK_FILE = ".claude/hooks/sentinel-edit-write";

  private static final String SETTINGS =
      """
            {
              "sentinelOwnership": "%s",
              "hooks": {
                "PostToolUse": [
                  {
                    "matcher": "^(Edit|Write|MultiEdit)$",
                    "hooks": [
                      {
                        "type": "command",
                        "command": ".claude/hooks/sentinel-edit-write"
                      }
                    ]
                  }
                ]
              }
            }
            """
          .replace("%s", SETTINGS_MARKER);

  private static final String HOOK =
      """
            #!/bin/sh
            # %s
            # Event payload is intentionally ignored; Sentinel checks the current project state.
            exec sentinel hook claude-code --learn-after 3
            """
          .replace("%s", HOOK_MARKER);

  @Override
  public String id() {
    return "claude-code";
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    final Path settings = projectRoot.toAbsolutePath().normalize().resolve(SETTINGS_FILE);
    final Path hook = projectRoot.toAbsolutePath().normalize().resolve(HOOK_FILE);
    try {
      return remove ? remove(settings, hook) : install(settings, hook);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not update Claude Code integration: " + e.getMessage(), e);
    }
  }

  private IntegrationResult install(final Path settings, final Path hook) throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(settings, SETTINGS_MARKER);
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
    if (conflict != null) {
      return conflict;
    }

    final List<String> changed = new ArrayList<>();
    final IntegrationResult settingsResult =
        IntegrationArtifacts.installOrRefresh(
            settings, SETTINGS_MARKER, SETTINGS, "Updated Sentinel Claude Code integration.");
    if (settingsResult.status() == IntegrationResult.Status.CONFLICT) {
      return settingsResult;
    }
    final IntegrationResult hookResult =
        IntegrationArtifacts.installOrRefresh(
            hook, HOOK_MARKER, HOOK, "Updated Sentinel Claude Code integration.");
    if (hookResult.status() == IntegrationResult.Status.CONFLICT) {
      return hookResult;
    }
    if (Files.exists(hook)) {
      makeExecutable(hook);
    }
    if (settingsResult.status() == IntegrationResult.Status.CHANGED) {
      changed.add(settings.toString());
    }
    if (hookResult.status() == IntegrationResult.Status.CHANGED) {
      changed.add(hook.toString());
    }
    return new IntegrationResult(
        changed.isEmpty()
            ? IntegrationResult.Status.ALREADY_PRESENT
            : IntegrationResult.Status.CHANGED,
        changed,
        changed.isEmpty()
            ? "Sentinel Claude Code integration is already present."
            : "Created Sentinel-owned Claude Code edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(final Path settings, final Path hook) throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(settings, SETTINGS_MARKER);
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
    if (conflict != null) {
      return conflict;
    }
    if (!Files.exists(settings) && !Files.exists(hook)) {
      return new IntegrationResult(
          IntegrationResult.Status.NOT_FOUND,
          List.of(),
          "No Sentinel Claude Code integration found.");
    }

    final List<String> removed = new ArrayList<>();
    if (Files.exists(settings)) {
      Files.delete(settings);
      removed.add(settings.toString());
    }
    if (Files.exists(hook)) {
      Files.delete(hook);
      removed.add(hook.toString());
    }
    deleteIfEmpty(hook.getParent());
    deleteIfEmpty(settings.getParent());
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED,
        removed,
        "Removed Sentinel-owned Claude Code integration.");
  }

  private static void deleteIfEmpty(final Path directory) throws IOException {
    if (directory != null && Files.isDirectory(directory)) {
      try (Stream<Path> entries = Files.list(directory)) {
        if (entries.findAny().isEmpty()) {
          Files.deleteIfExists(directory);
        }
      }
    }
  }

  private static void makeExecutable(final Path file) throws IOException {
    try {
      final Set<PosixFilePermission> permissions =
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(file, permissions);
    } catch (UnsupportedOperationException ignored) {
      file.toFile().setExecutable(true, false);
    }
  }
}
