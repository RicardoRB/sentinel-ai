package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Safe, ownership-marked integration for OpenCode command discovery and edit/write events. */
public final class OpenCodeIntegration implements AgentIntegration {
  public static final String MARKER = "<!-- sentinel-owned: opencode-quality-check v1 -->";
  public static final String PLUGIN_MARKER = "sentinel-owned: opencode-edit-write v1";
  public static final String GUARD_MARKER = "sentinel-owned: opencode-openspec-guard v1";
  private static final String COMMAND_FILE = ".opencode/commands/sentinel-check.md";
  private static final String PLUGIN_FILE = ".opencode/plugins/sentinel-edit-write.js";
  private static final String GUARD_FILE = ".opencode/plugins/sentinel-openspec-guard.js";

  private static final String COMMAND =
      MARKER + "\n\nRun `sentinel check --format json --learn-after 3` and return the result.\n";
  private static final String PLUGIN =
      """
            // %s
            import { define } from "@opencode/plugin";

            // Native OpenCode V2 quality and idle validation plugin.
            // V2 hook: tool.execute.after; idle event: session.idle.
            const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "apply_patch", "patch"]);
            const FEEDBACK_MARKER = "[sentinel spec validation]";
            const decode = (bytes) => new TextDecoder().decode(bytes || new Uint8Array());
            const sessions = new Map();
            const pathValues = (input, result) => {
              const args = input ?? {};
              const files = Array.isArray(result?.metadata?.files) ? result.metadata.files : [];
              return [args.filePath, args.path, ...files.flatMap((file) => [file?.filePath, file?.movePath])]
                .filter((path) => typeof path === "string" && path.includes("openspec"));
            };
            const stateFor = (sessionID) => {
              let state = sessions.get(sessionID);
              if (!state) {
                state = { running: false, generation: 0, delivered: false, directory: null, process: null };
                sessions.set(sessionID, state);
              }
              return state;
            };
            const diagnostic = (message) => console.warn(`[sentinel] ${String(message).slice(0, 500)}`);

            export default define({
              id: "sentinel.edit-write",
              setup: async (ctx) => {
                const abort = new AbortController();
                const run = async (args, directory) => {
                  const proc = Bun.spawn(["sentinel", ...args], { cwd: directory, stdout: "pipe", stderr: "pipe", signal: abort.signal });
                  const [stdout, stderr, code] = await Promise.all([new Response(proc.stdout).text(), new Response(proc.stderr).text(), proc.exited]);
                  return { code, stdout, stderr };
                };
                const locationFor = async (sessionID) => {
                  try {
                    const session = await ctx.session.get({ sessionID });
                    const directory = session?.data?.directory ?? session?.directory ?? ctx.location.directory;
                    return typeof directory === "string" ? directory : null;
                  } catch (error) {
                    diagnostic(`session location unavailable: ${error?.message ?? error}`);
                    return null;
                  }
                };
                const after = await ctx.tool.hook("execute.after", async (event) => {
                  if (event.status !== "completed" || !EDIT_TOOLS.has(String(event.tool).toLowerCase())) return;
                  const directory = await locationFor(event.sessionID);
                  if (!directory) return diagnostic("skipping post-edit validation without a session location");
                  const check = await run(["check", "--format", "json", "--exclude-spec", "--learn-after", "3"], directory);
                  let report = null;
                  try { report = JSON.parse(check.stdout); } catch { /* Sentinel errors are reported below. */ }
                  const prompts = report?.learning?.prompts?.map((prompt) => prompt.instruction).join("\\n") || "";
                  const failures = [];
                  if (check.code !== 0) failures.push(check.stdout || check.stderr || "Sentinel quality check failed");
                  const paths = pathValues(event.input, event.result);
                  if (paths.length > 0) {
                    const spec = await run(["spec", "validate", "--", ...paths], directory);
                    if (spec.code !== 0) failures.push(spec.stdout || spec.stderr || "Sentinel spec validation failed");
                  }
                  const feedback = failures.concat(prompts).filter(Boolean).join("\\n");
                  if (feedback) {
                    if (event.result && typeof event.result === "object") event.result.output = `${event.result.output || ""}\\n${feedback}`.trim();
                    if (failures.length > 0) throw new Error(feedback);
                  }
                });
                const eventStream = ctx.event.subscribe();
                const subscription = (async () => { for await (const event of eventStream) {
                  if (abort.signal.aborted || event?.type !== "session.idle") continue;
                  const sessionID = event.properties?.sessionID ?? event.sessionID;
                  if (!sessionID) continue;
                  const state = stateFor(sessionID);
                  if (state.running || state.delivered) continue;
                  const directory = await locationFor(sessionID);
                  if (!directory) { diagnostic("skipping idle validation without a session location"); continue; }
                  state.running = true; state.directory = directory; const generation = state.generation;
                  try {
                    const result = await run(["spec", "validate"], directory);
                    if (result.code === 0) continue;
                    const findings = (result.stdout || result.stderr || "Sentinel spec validation failed").trim();
                    if (result.code !== 1) { diagnostic(`idle validation failed: ${result.code}`); continue; }
                    if (state.generation !== generation || state.directory !== directory || state.delivered) { diagnostic(findings); continue; }
                    state.delivered = true;
                    await ctx.session.prompt({ sessionID, prompt: { parts: [{ type: "text", text: `${FEEDBACK_MARKER}\\n${findings}` }] }, metadata: { sentinel: FEEDBACK_MARKER }, delivery: "followup" });
                  } catch (error) {
                    diagnostic(`idle validation error: ${error?.message ?? error}`);
                  } finally { state.running = false; }
                } })().catch((error) => diagnostic(`idle subscription stopped: ${error?.message ?? error}`));
                const prompt = await ctx.session.hook("prompt", async (event) => {
                  const state = stateFor(event.sessionID);
                  if (event.metadata?.sentinel === FEEDBACK_MARKER || event.metadata?.sentinel) return;
                  state.generation++; state.delivered = false;
                });
                return async () => { abort.abort(); sessions.clear(); await after.dispose(); await prompt.dispose(); await subscription; };
              },
            });
            """
          .replace("%s", PLUGIN_MARKER);

  private static final String GUARD_PLUGIN =
      """
            // %s
            import { define } from "@opencode/plugin";
            // Native OpenCode V2 approved-artifact guard.
            // V2 hook: tool.execute.before.
            const decode = (bytes) => new TextDecoder().decode(bytes || new Uint8Array());
            const warning = (message) => console.warn(`[sentinel guard] ${String(message).slice(0, 500)}`);

            export default define({
              id: "sentinel.openspec-guard",
              setup: async (ctx) => {
                const run = async (args, input, directory) => {
                  const proc = Bun.spawn(["sentinel", ...args], { cwd: directory, stdin: input === undefined ? undefined : new TextEncoder().encode(input), stdout: "pipe", stderr: "pipe" });
                  const [stdout, stderr, code] = await Promise.all([new Response(proc.stdout).text(), new Response(proc.stderr).text(), proc.exited]);
                  const exitCode = code;
                  return { code: exitCode, stdout: decode(new TextEncoder().encode(stdout)), stderr: decode(new TextEncoder().encode(stderr)) };
                };
                const before = await ctx.tool.hook("execute.before", async (event) => {
                  const tool = String(event.tool ?? "").toLowerCase();
                  const args = event.input ?? {};
                  const directory = (await ctx.session.get({ sessionID: event.sessionID }))?.data?.directory ?? ctx.location.directory;
                  let command;
                  let stdin;
                  if (tool === "edit" || tool === "write") {
                    command = ["spec", "guard", "--", args.filePath ?? args.path];
                  } else if (tool === "apply_patch" || tool === "patch") {
                    command = ["spec", "guard", "--patch-stdin"];
                    stdin = args.patchText ?? args.patch;
                  } else if (tool === "shell" || tool === "bash") {
                    command = ["spec", "guard", "--shell-stdin", "--cwd", args.workdir ?? directory];
                    stdin = args.command;
                  } else if (tool === "skill") {
                    command = ["spec", "guard", "--skill", args.name ?? args.skill];
                  } else {
                    return;
                  }

                  if (command.some((value) => value === undefined || value === null)) command = ["spec", "guard", "--unreadable-input"];
                  let result;
                  try {
                    result = await run(command, stdin, directory);
                  } catch (error) {
                    warning("Sentinel approved-artifact guard could not run: " + (error?.message ?? error));
                    return;
                  }
                  if (result.code === 1) throw new Error(result.stdout.trim() || "Sentinel approved-artifact guard refused the tool call.");
                  if (result.code !== 0) {
                    warning(result.stderr.trim() || "Sentinel approved-artifact guard could not decide; proceeding.");
                  }
                });
                return () => before.dispose();
              },
            });
            """
          .replace("%s", GUARD_MARKER);

  @Override
  public String id() {
    return "opencode";
  }

  @Override
  public Set<IntegrationCapability> capabilities() {
    return Set.of(
        IntegrationCapability.POST_EDIT,
        IntegrationCapability.TURN_END,
        IntegrationCapability.FEEDBACK,
        IntegrationCapability.APPROVED_ARTIFACT_GUARD);
  }

  @Override
  public boolean installed(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    return Files.isRegularFile(root.resolve(COMMAND_FILE))
        && Files.isRegularFile(root.resolve(PLUGIN_FILE));
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final Path command = root.resolve(COMMAND_FILE);
    final Path plugin = root.resolve(PLUGIN_FILE);
    final GuardFile guard = new GuardFile(root.resolve(GUARD_FILE), GUARD_MARKER, GUARD_PLUGIN);
    try {
      return request.remove()
          ? remove(command, plugin, guard)
          : install(command, plugin, guard, request.guard());
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update OpenCode integration: " + e.getMessage(), e);
    }
  }

  private IntegrationResult install(
      final Path command, final Path plugin, final GuardFile guard, final GuardMode mode)
      throws IOException {
    IntegrationResult conflict =
        IntegrationArtifacts.preflight(command, MARKER, plugin, PLUGIN_MARKER);
    if (conflict == null) {
      conflict = guard.preflight(mode);
    }
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
    final List<Map.Entry<Path, IntegrationResult>> results =
        new ArrayList<>(
            List.of(Map.entry(command, commandResult), Map.entry(plugin, pluginResult)));
    if (guard.enabled(mode)) {
      results.add(guard.install("Updated Sentinel OpenCode approved-artifact guard."));
    }
    guard.remove(mode == GuardMode.DISABLE).ifPresent(results::add);
    return IntegrationArtifacts.combine(
        results,
        "Sentinel OpenCode integration is already present.",
        "Created Sentinel-owned OpenCode command and edit/write integration (or refreshed it).");
  }

  private IntegrationResult remove(final Path command, final Path plugin, final GuardFile guard)
      throws IOException {
    IntegrationResult conflict = IntegrationArtifacts.preflight(command, MARKER);
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(plugin, PLUGIN_MARKER);
    if (conflict != null) {
      return conflict;
    }
    conflict = IntegrationArtifacts.preflight(guard.file(), GUARD_MARKER);
    if (conflict != null) {
      return conflict;
    }
    if (!Files.exists(command) && !Files.exists(plugin) && !guard.installed()) {
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
    guard.remove(true).ifPresent(entry -> removed.add(entry.getKey().toString()));
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
