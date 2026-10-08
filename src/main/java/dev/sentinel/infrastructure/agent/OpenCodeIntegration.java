package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Safe, ownership-marked integration for OpenCode command discovery and edit/write events. */
public final class OpenCodeIntegration implements AgentIntegration {
  public static final String MARKER = "<!-- sentinel-owned: opencode-quality-check v1 -->";
  public static final String PLUGIN_MARKER = "sentinel-owned: opencode-edit-write v1";
  private static final String COMMAND_FILE = ".opencode/commands/sentinel-check.md";
  private static final String PLUGIN_FILE = ".opencode/plugins/sentinel-edit-write.js";

  private static final String COMMAND =
      MARKER + "\n\nRun `sentinel check --format json --learn-after 3` and return the result.\n";
  private static final String PLUGIN =
      """
            // %s
            // OpenCode post-tool hook for Edit and Write operations.
            export default async ({ directory }) => ({
              "tool.execute.after": async (input, output) => {
                const tool = String(input?.tool ?? input?.name ?? "").toLowerCase();
                if (tool !== "edit" && tool !== "write" && tool !== "multiedit" && tool !== "patch") return;
                 const result = Bun.spawnSync(["sentinel", "check", "--format", "json", "--learn-after", "3"], {
                  cwd: directory,
                  stdout: "pipe",
                  stderr: "pipe"
                });
                 const text = new TextDecoder().decode(result.stdout || new Uint8Array());
                 let report;
                 try { report = JSON.parse(text); } catch { report = null; }
                 const prompts = report?.learning?.prompts?.map((prompt) => prompt.instruction).join("\\n") || "";
                 if (result.exitCode !== 0) {
                   output.error = (text || new TextDecoder().decode(result.stderr || new Uint8Array())) + (prompts ? "\\n" + prompts : "");
                   throw new Error(output.error || "Sentinel quality check failed");
                 }
                 if (prompts) output.output = (output.output || "") + "\\n" + prompts;
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
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(plugin, PLUGIN_MARKER);
    if (conflict != null) {
      return conflict;
    }

    List<String> changed = new ArrayList<>();
    IntegrationResult commandResult =
        IntegrationArtifacts.installOrRefresh(
            command, MARKER, COMMAND, "Updated Sentinel OpenCode integration.");
    if (commandResult.status() == IntegrationResult.Status.CONFLICT) {
      return commandResult;
    }
    IntegrationResult pluginResult =
        IntegrationArtifacts.installOrRefresh(
            plugin, PLUGIN_MARKER, PLUGIN, "Updated Sentinel OpenCode integration.");
    if (pluginResult.status() == IntegrationResult.Status.CONFLICT) {
      return pluginResult;
    }
    if (commandResult.status() == IntegrationResult.Status.CHANGED) {
      changed.add(command.toString());
    }
    if (pluginResult.status() == IntegrationResult.Status.CHANGED) {
      changed.add(plugin.toString());
    }
    return new IntegrationResult(
        changed.isEmpty()
            ? IntegrationResult.Status.ALREADY_PRESENT
            : IntegrationResult.Status.CHANGED,
        changed,
        changed.isEmpty()
            ? "Sentinel OpenCode integration is already present."
            : "Created Sentinel-owned OpenCode command and edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(Path command, Path plugin) throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(command, MARKER);
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(plugin, PLUGIN_MARKER);
    if (conflict != null) {
      return conflict;
    }
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
        if (entries.findAny().isEmpty()) {
          Files.deleteIfExists(directory);
        }
      }
    }
  }
}
