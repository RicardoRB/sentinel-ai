package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Safe, ownership-marked integration for OpenCode command discovery and edit/write events. */
public final class OpenCodeIntegration implements AgentIntegration {
  public static final String MARKER = "<!-- sentinel-owned: opencode-quality-check v1 -->";
  public static final String PLUGIN_MARKER = "sentinel-owned: opencode-edit-write v1";
  private static final String COMMAND_FILE = ".opencode/commands/sentinel-check.md";
  private static final String PLUGIN_FILE = ".opencode/plugins/sentinel-edit-write.js";

  private static final String COMMAND =
      MARKER + "\n\nRun `sentinel check --format json` and return the result.\n";
  private static final String PLUGIN =
      """
            // %s
            // OpenCode post-tool hook for Edit and Write operations.
            export default async ({ directory }) => ({
              "tool.execute.after": async (input, output) => {
                const tool = String(input?.tool ?? input?.name ?? "").toLowerCase();
                if (tool !== "edit" && tool !== "write" && tool !== "multiedit" && tool !== "patch") return;
                const result = Bun.spawnSync(["./verify-quality.sh"], {
                  cwd: directory,
                  stdout: "pipe",
                  stderr: "pipe"
                });
                const text = new TextDecoder().decode(result.stdout || new Uint8Array());
                if (result.exitCode !== 0) {
                  output.error = text || new TextDecoder().decode(result.stderr || new Uint8Array());
                  throw new Error(output.error || "Sentinel quality check failed");
                }
              }
            });
            """
          .replace("%s", PLUGIN_MARKER);

  @Override
  public String id() {
    return "opencode";
  }

  @Override
  public IntegrationResult integrate(Path projectRoot, boolean remove) {
    Path root = projectRoot.toAbsolutePath().normalize();
    Path command = root.resolve(COMMAND_FILE);
    Path plugin = root.resolve(PLUGIN_FILE);
    try {
      return remove ? remove(command, plugin) : install(command, plugin);
    } catch (IOException e) {
      throw new RuntimeException("Could not update OpenCode integration: " + e.getMessage(), e);
    }
  }

  private IntegrationResult install(Path command, Path plugin) throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(command, MARKER);
    if (conflict != null) return conflict;
    conflict = IntegrationArtifacts.preflight(plugin, PLUGIN_MARKER);
    if (conflict != null) return conflict;

    List<String> changed = new ArrayList<>();
    if (!Files.exists(command)) {
      Files.createDirectories(Objects.requireNonNull(command.getParent()));
      Files.writeString(command, COMMAND);
      changed.add(command.toString());
    }
    if (!Files.exists(plugin)) {
      Files.createDirectories(Objects.requireNonNull(plugin.getParent()));
      Files.writeString(plugin, PLUGIN);
      changed.add(plugin.toString());
    }
    return changed.isEmpty()
        ? new IntegrationResult(
            IntegrationResult.Status.ALREADY_PRESENT,
            List.of(),
            "Sentinel OpenCode integration is already present.")
        : new IntegrationResult(
            IntegrationResult.Status.CHANGED,
            changed,
            "Created Sentinel-owned OpenCode command and edit/write integration.");
  }

  private IntegrationResult remove(Path command, Path plugin) throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(command, MARKER);
    if (conflict != null) return conflict;
    conflict = IntegrationArtifacts.preflight(plugin, PLUGIN_MARKER);
    if (conflict != null) return conflict;
    if (!Files.exists(command) && !Files.exists(plugin)) {
      return new IntegrationResult(
          IntegrationResult.Status.NOT_FOUND, List.of(), "No Sentinel OpenCode integration found.");
    }

    List<String> removed = new ArrayList<>();
    if (Files.exists(command)) {
      Files.delete(command);
      removed.add(command.toString());
    }
    if (Files.exists(plugin)) {
      Files.delete(plugin);
      removed.add(plugin.toString());
    }
    deleteIfEmpty(plugin.getParent());
    deleteIfEmpty(command.getParent());
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED, removed, "Removed Sentinel-owned OpenCode integration.");
  }

  private static void deleteIfEmpty(Path directory) throws IOException {
    if (directory != null && Files.isDirectory(directory)) {
      try (var entries = Files.list(directory)) {
        if (entries.findAny().isEmpty()) Files.deleteIfExists(directory);
      }
    }
  }
}
