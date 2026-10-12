package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
            // OpenCode hooks: quality check and spec validation after edits, spec checks when idle.
            const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "apply_patch", "patch"]);
            const FEEDBACK_MARKER = "[sentinel spec validation]";
            const decode = (bytes) => new TextDecoder().decode(bytes || new Uint8Array());

            const editedPaths = (input, output) => {
              const args = input?.args ?? {};
              const files = Array.isArray(output?.metadata?.files) ? output.metadata.files : [];
              return [args.filePath, args.path, ...files.flatMap((file) => [file?.filePath, file?.movePath])]
                // Cheap pre-filter; Sentinel decides which paths are active spec items.
                .filter((path) => typeof path === "string" && path.includes("openspec"));
            };

            export default async ({ directory, client }) => {
              const run = (args) => {
                const result = Bun.spawnSync(["sentinel", ...args], { cwd: directory, stdout: "pipe", stderr: "pipe" });
                return { code: result.exitCode, stdout: decode(result.stdout), stderr: decode(result.stderr) };
              };
              const running = new Set();
              const delivered = new Set();
              const toast = (message) =>
                client?.tui?.showToast?.({ body: { title: "Sentinel", message, variant: "warning" } })?.catch?.(() => {});

              return {
                "tool.execute.after": async (input, output) => {
                  const tool = String(input?.tool ?? input?.name ?? "").toLowerCase();
                  if (!EDIT_TOOLS.has(tool)) return;
                  const check = run(["check", "--format", "json", "--exclude-spec", "--learn-after", "3"]);
                  let report;
                  try { report = JSON.parse(check.stdout); } catch { report = null; }
                  const prompts = report?.learning?.prompts?.map((prompt) => prompt.instruction).join("\\n") || "";
                  const failures = [];
                  if (check.code !== 0) failures.push(check.stdout || check.stderr || "Sentinel quality check failed");
                  const paths = editedPaths(input, output);
                  if (paths.length > 0) {
                    const spec = run(["spec", "validate", "--", ...paths]);
                    if (spec.code !== 0) failures.push(spec.stdout || spec.stderr || "Sentinel spec validation failed");
                  }
                  if (failures.length > 0) {
                    output.error = failures.join("\\n") + (prompts ? "\\n" + prompts : "");
                    throw new Error(output.error);
                  }
                  if (prompts) output.output = (output.output || "") + "\\n" + prompts;
                },
                event: async ({ event }) => {
                  if (event?.type !== "session.idle") return;
                  const sessionID = event.properties?.sessionID;
                  if (!sessionID || running.has(sessionID)) return;
                  running.add(sessionID);
                  try {
                    const proc = Bun.spawn(["sentinel", "spec", "validate"], { cwd: directory, stdout: "pipe", stderr: "pipe" });
                    const [stdout, stderr, code] = await Promise.all([
                      new Response(proc.stdout).text(),
                      new Response(proc.stderr).text(),
                      proc.exited,
                    ]);
                    if (code === 0) return;
                    const findings = (stdout || stderr || "Sentinel spec validation failed").trim();
                    if (code === 1 && !delivered.has(sessionID) && client?.session?.prompt) {
                      delivered.add(sessionID);
                      // Not awaited: the prompt starts a new agent turn whose idle event is checked again.
                      client.session
                        .prompt({ path: { id: sessionID }, body: { parts: [{ type: "text", text: FEEDBACK_MARKER + "\\n" + findings }] } })
                        .catch(() => toast(findings));
                    } else {
                      toast(findings);
                    }
                  } catch (error) {
                    toast("Sentinel spec validation could not run: " + (error?.message ?? error));
                  } finally {
                    running.delete(sessionID);
                  }
                },
                "chat.message": async (input, output) => {
                  const own = (output?.parts ?? []).some(
                    (part) => part?.type === "text" && String(part.text ?? "").startsWith(FEEDBACK_MARKER),
                  );
                  if (!own) delivered.delete(input?.sessionID);
                },
              };
            };
            """
          .replace("%s", PLUGIN_MARKER);

  @Override
  public String id() {
    return "opencode";
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path command = root.resolve(COMMAND_FILE);
    final Path plugin = root.resolve(PLUGIN_FILE);
    try {
      return remove ? remove(command, plugin) : install(command, plugin);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update OpenCode integration: " + e.getMessage(), e);
    }
  }

  private IntegrationResult install(final Path command, final Path plugin) throws IOException {
    final IntegrationResult conflict =
        IntegrationArtifacts.preflight(command, MARKER, plugin, PLUGIN_MARKER);
    if (conflict != null) {
      return conflict;
    }
    final IntegrationResult commandResult =
        IntegrationArtifacts.installOrRefresh(
            command, MARKER, COMMAND, "Updated Sentinel OpenCode integration.");
    if (commandResult.status() == IntegrationResult.Status.CONFLICT) {
      return commandResult;
    }
    final IntegrationResult pluginResult =
        IntegrationArtifacts.installOrRefresh(
            plugin, PLUGIN_MARKER, PLUGIN, "Updated Sentinel OpenCode integration.");
    if (pluginResult.status() == IntegrationResult.Status.CONFLICT) {
      return pluginResult;
    }
    return IntegrationArtifacts.combine(
        command,
        commandResult,
        plugin,
        pluginResult,
        "Sentinel OpenCode integration is already present.",
        "Created Sentinel-owned OpenCode command and edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(final Path command, final Path plugin) throws IOException {
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

    final List<String> removed = new ArrayList<>();
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

  private static void deleteIfEmpty(final Path directory) throws IOException {
    if (directory != null && Files.isDirectory(directory)) {
      try (Stream<Path> entries = Files.list(directory)) {
        if (entries.findAny().isEmpty()) {
          Files.deleteIfExists(directory);
        }
      }
    }
  }
}
