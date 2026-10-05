package dev.sentinel.application;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Ownership-safe Claude Code project hook integration. */
public final class ClaudeCodeIntegration implements AgentIntegration {
    public static final String SETTINGS_MARKER = "sentinel-owned: claude-code-edit-write v1";
    public static final String HOOK_MARKER = "sentinel-owned: edit-write-guard v1";
    private static final String SETTINGS_FILE = ".claude/settings.json";
    private static final String HOOK_FILE = ".claude/hooks/sentinel-edit-write";

    private static final String SETTINGS = """
            {
              "sentinelOwnership": "%s",
              "hooks": {
                "PostToolUse": [
                  {
                    "matcher": "^(Edit|Write)$",
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
            """.formatted(SETTINGS_MARKER);

    private static final String HOOK = """ 
            #!/bin/sh
            # %s
            # Event payload is intentionally ignored; Sentinel checks the current project state.
            exec sentinel check --format json
            """.formatted(HOOK_MARKER);

    @Override
    public String id() {
        return "claude-code";
    }

    @Override
    public IntegrationResult integrate(Path projectRoot, boolean remove) {
        Path settings = projectRoot.toAbsolutePath().normalize().resolve(SETTINGS_FILE);
        Path hook = projectRoot.toAbsolutePath().normalize().resolve(HOOK_FILE);
        try {
            return remove ? remove(settings, hook) : install(settings, hook);
        } catch (IOException e) {
            throw new RuntimeException("Could not update Claude Code integration: " + e.getMessage(), e);
        }
    }

    private IntegrationResult install(Path settings, Path hook) throws IOException {
        IntegrationResult conflict = IntegrationArtifacts.preflight(settings, SETTINGS_MARKER);
        if (conflict != null) return conflict;
        conflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
        if (conflict != null) return conflict;

        List<String> changed = new ArrayList<>();
        if (!Files.exists(settings)) {
            Files.createDirectories(settings.getParent());
            Files.writeString(settings, SETTINGS);
            changed.add(settings.toString());
        }
        if (!Files.exists(hook)) {
            Files.createDirectories(hook.getParent());
            Files.writeString(hook, HOOK);
            makeExecutable(hook);
            changed.add(hook.toString());
        }
        return changed.isEmpty()
                ? new IntegrationResult(IntegrationResult.Status.ALREADY_PRESENT, List.of(),
                "Sentinel Claude Code integration is already present.")
                : new IntegrationResult(IntegrationResult.Status.CHANGED, changed,
                "Created Sentinel-owned Claude Code edit/write integration.");
    }

    private IntegrationResult remove(Path settings, Path hook) throws IOException {
        IntegrationResult conflict = IntegrationArtifacts.preflight(settings, SETTINGS_MARKER);
        if (conflict != null) return conflict;
        conflict = IntegrationArtifacts.preflight(hook, HOOK_MARKER);
        if (conflict != null) return conflict;
        if (!Files.exists(settings) && !Files.exists(hook)) {
            return new IntegrationResult(IntegrationResult.Status.NOT_FOUND, List.of(),
                    "No Sentinel Claude Code integration found.");
        }

        List<String> removed = new ArrayList<>();
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
        return new IntegrationResult(IntegrationResult.Status.REMOVED, removed,
                "Removed Sentinel-owned Claude Code integration.");
    }

    private static void deleteIfEmpty(Path directory) throws IOException {
        if (directory != null && Files.isDirectory(directory)) {
            try (var entries = Files.list(directory)) {
                if (entries.findAny().isEmpty()) Files.deleteIfExists(directory);
            }
        }
    }

    private static void makeExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException ignored) {
            file.toFile().setExecutable(true, false);
        }
    }
}
