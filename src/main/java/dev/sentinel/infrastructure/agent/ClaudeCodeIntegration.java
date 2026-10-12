package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
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
  public static final String GUIDANCE_FILE =
      OpenSpecGuidanceLayout.forAgent("claude-code").orElseThrow().guidanceFile();

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
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path hook = root.resolve(HOOK_FILE);
    final List<IntegrationArtifacts.Target> targets = new ArrayList<>();
    if (request.coversHooks()) {
      targets.add(
          new IntegrationArtifacts.Target(root.resolve(SETTINGS_FILE), SETTINGS_MARKER, SETTINGS));
      targets.add(new IntegrationArtifacts.Target(hook, HOOK_MARKER, HOOK));
    }
    if (request.coversGuidance()) {
      targets.add(
          new IntegrationArtifacts.Target(
              root.resolve(GUIDANCE_FILE), OpenSpecGuidance.MARKER, OpenSpecGuidance.content()));
    }
    final String subject =
        request.coversHooks() ? "Claude Code integration" : "Claude Code OpenSpec guidance";
    try {
      return request.removal()
          ? remove(root, targets, subject)
          : install(hook, targets, subject, request.coversHooks());
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Could not update Claude Code integration: " + e.getMessage(), e);
    }
  }

  private static IntegrationResult install(
      final Path hook,
      final List<IntegrationArtifacts.Target> targets,
      final String subject,
      final boolean hooks)
      throws IOException {
    final IntegrationResult result =
        IntegrationArtifacts.installOrRefreshAll(
            targets,
            "Sentinel " + subject + " is already present.",
            hooks
                ? "Created Sentinel-owned Claude Code edit/write integration (or refreshed it)."
                : "Created Sentinel-owned " + subject + " (or refreshed it).");
    if (hooks && result.status() != IntegrationResult.Status.CONFLICT && Files.exists(hook)) {
      makeExecutable(hook);
    }
    return result;
  }

  private static IntegrationResult remove(
      final Path root, final List<IntegrationArtifacts.Target> targets, final String subject)
      throws IOException {
    return IntegrationArtifacts.removeAll(
        targets,
        root,
        "No Sentinel " + subject + " found.",
        "Removed Sentinel-owned " + subject + ".");
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
