package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Safe, ownership-marked integration for OpenCode command discovery and edit/write events. */
public final class OpenCodeIntegration implements AgentIntegration {
  public static final String MARKER = "<!-- ward-owned: opencode-quality-check v1 -->";
  public static final String PLUGIN_MARKER = "ward-owned: opencode-edit-write v1";
  public static final String GUARD_MARKER = "ward-owned: opencode-openspec-guard v1";
  private static final OpenCodeFiles ROOT_FILES = new OpenCodeFiles("");

  private static final String COMMAND =
      MARKER + "\n\nRun `ward check --format json --learn-after 3` and return the result.\n";
  private static final String PLUGIN =
      """
            // %s
            import { Plugin } from "@opencode/plugin";

            // Native OpenCode V2 quality and idle validation plugin (@opencode/plugin 2.0.30).
            // Hooks: tool "execute.after" and the session.idle event. The host treats an
            // execute.after rejection as a defect, so feedback is appended to the content the model
            // reads instead of being thrown.
            const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "apply_patch", "patch"]);
            const CHECK = ["check", "--format", "json", "--exclude-spec", "--learn-after", "3"];
            const FEEDBACK_MARKER = "[ward spec validation]";
            const MAX_FEEDBACK = 20000;
            const bound = (text) => (text.length > MAX_FEEDBACK ? `${text.slice(0, MAX_FEEDBACK)}\\n[truncated]` : text);
            const diagnostic = (message) => console.warn(`[ward] ${String(message).slice(0, 500)}`);
            const editedPaths = (input, result) => {
              const args = input && typeof input === "object" ? input : {};
              const files = Array.isArray(result?.metadata?.files) ? result.metadata.files : [];
              const paths = [args.filePath, args.path, ...files.flatMap((file) => [file?.filePath, file?.movePath])];
              return [...new Set(paths.filter((path) => typeof path === "string" && path))];
            };
            const editedSpecPaths = (paths) => paths.filter((path) => path.includes("openspec"));
            // Mirrors the host's content normalization so appended feedback keeps the original result.
            const contentOf = (result) => {
              const content = result?.content;
              if (typeof content === "string") return [{ type: "text", text: content }];
              if (Array.isArray(content) && content.length > 0) return [...content];
              const output = result?.output;
              return [{ type: "text", text: typeof output === "string" ? output : (JSON.stringify(output) ?? String(output)) }];
            };

            export default Plugin.define({
              id: "ward.edit-write",
              setup: async (ctx) => {
                const abort = new AbortController();
                const sessions = new Map();
                const stateFor = (sessionID) => {
                  let state = sessions.get(sessionID);
                  if (!state) {
                    state = { running: false, generation: 0, delivered: false };
                    sessions.set(sessionID, state);
                  }
                  return state;
                };
                const run = async (args, directory) => {
                  try {
                    const proc = Bun.spawn(["ward", ...args], { cwd: directory, stdout: "pipe", stderr: "pipe", signal: abort.signal });
                    const [stdout, stderr, code] = await Promise.all([new Response(proc.stdout).text(), new Response(proc.stderr).text(), proc.exited]);
                    return { code, stdout, stderr };
                  } catch (error) {
                    return { code: null, stdout: "", stderr: "", error: String(error?.message ?? error).slice(0, 500) };
                  }
                };
                // The session's current location is authoritative; a failed lookup skips validation
                // rather than guessing a directory.
                const locationFor = async (sessionID) => {
                  try {
                    const directory = (await ctx.session.get({ sessionID }))?.location?.directory;
                    if (typeof directory === "string" && directory) return directory;
                    diagnostic("session has no location; skipping validation");
                  } catch (error) {
                    diagnostic(`session location unavailable: ${error?.message ?? error}`);
                  }
                  return null;
                };
                const validateEdit = async (event, directory) => {
                  const paths = editedPaths(event.input, event.result);
                  const checkArgs = paths.flatMap((path) => ["--path", path]);
                  const check = await run([...CHECK, ...checkArgs], directory);
                  if (check.error) return `Ward could not run the quality check: ${check.error}`;
                  let report = null;
                  try { report = JSON.parse(check.stdout); } catch { /* Non-JSON output is reported as text. */ }
                  const learning = (report?.learning?.prompts ?? [])
                    .map((prompt) => prompt?.instruction)
                    .filter((instruction) => typeof instruction === "string" && instruction);
                  const parts = [];
                  if (check.code !== 0) parts.push(`Ward quality check failed:\\n${bound((check.stdout || check.stderr).trim())}`);
                  const specPaths = editedSpecPaths(paths);
                  if (specPaths.length > 0) {
                    const spec = await run(["spec", "validate", "--", ...specPaths], directory);
                    if (spec.error) parts.push(`Ward could not run spec validation: ${spec.error}`);
                    else if (spec.code !== 0) parts.push(`Ward spec validation failed:\\n${bound((spec.stdout || spec.stderr).trim())}`);
                  }
                  return parts.concat(learning).join("\\n");
                };
                const after = await ctx.tool.hook("execute.after", async (event) => {
                  if (abort.signal.aborted || event.status !== "completed" || !EDIT_TOOLS.has(String(event.tool).toLowerCase())) return;
                  try {
                    const directory = await locationFor(event.sessionID);
                    if (!directory) return;
                    const feedback = await validateEdit(event, directory);
                    if (feedback && !abort.signal.aborted) {
                      event.result = { ...event.result, content: [...contentOf(event.result), { type: "text", text: feedback }] };
                    }
                  } catch (error) {
                    diagnostic(`post-edit validation error: ${error?.message ?? error}`);
                  }
                });
                const validateIdle = async (sessionID) => {
                  const state = stateFor(sessionID);
                  if (state.running) return;
                  state.running = true;
                  const generation = state.generation;
                  try {
                    const directory = await locationFor(sessionID);
                    if (!directory) return;
                    const result = await run(["spec", "validate"], directory);
                    if (abort.signal.aborted) return;
                    if (result.error) return diagnostic(`idle validation could not run: ${result.error}`);
                    if (result.code === 0) return;
                    if (result.code !== 1) return diagnostic(`idle validation failed with exit ${result.code}`);
                    const findings = bound((result.stdout || result.stderr).trim() || "Ward spec validation reported findings.");
                    if (state.generation !== generation) return diagnostic("dropping idle findings from a previous turn");
                    if (state.delivered) return diagnostic(findings);
                    state.delivered = true;
                    await ctx.session.prompt({ sessionID, text: `${FEEDBACK_MARKER}\\n${findings}`, metadata: { ward: FEEDBACK_MARKER }, delivery: "queue" });
                  } catch (error) {
                    diagnostic(`idle validation error: ${error?.message ?? error}`);
                  } finally {
                    state.running = false;
                  }
                };
                const subscription = (async () => {
                  for await (const event of ctx.event.subscribe({ signal: abort.signal })) {
                    if (abort.signal.aborted) break;
                    const sessionID = event?.type === "session.idle" ? event.data?.sessionID : undefined;
                    // Not awaited: overlapping idle events for a running session are ignored.
                    if (typeof sessionID === "string" && sessionID) void validateIdle(sessionID);
                  }
                })().catch((error) => {
                  if (!abort.signal.aborted) diagnostic(`idle subscription stopped: ${error?.message ?? error}`);
                });
                const prompt = await ctx.session.hook("prompt", (event) => {
                  // Ward's own hand-off is not a new user turn.
                  if (event.metadata?.ward === FEEDBACK_MARKER) return;
                  const state = stateFor(event.sessionID);
                  state.generation++;
                  state.delivered = false;
                });
                return async () => {
                  abort.abort();
                  await Promise.allSettled([after.dispose(), prompt.dispose()]);
                  await subscription;
                  sessions.clear();
                };
              },
            });
            """
          .replace("%s", PLUGIN_MARKER);

  private static final String GUARD_PLUGIN =
      """
            // %s
            import { isAbsolute, join } from "node:path";
            import { Plugin } from "@opencode/plugin";

            // Native OpenCode V2 approved-artifact guard (@opencode/plugin 2.0.30).
            // Hook: tool "execute.before". A rejection refuses that call alone. When Ward cannot
            // decide, the call proceeds with a bounded warning.
            const GUARDED = new Set(["edit", "write", "apply_patch", "patch", "bash", "shell", "skill"]);
            const warning = (message) => console.warn(`[ward guard] ${String(message).slice(0, 500)}`);
            const text = (value) => (typeof value === "string" && value ? value : undefined);
            const guardCommand = (tool, args, directory) => {
              if (tool === "edit" || tool === "write") {
                const path = text(args.filePath) ?? text(args.path);
                if (path) return { command: ["spec", "guard", "--", path] };
              } else if (tool === "apply_patch" || tool === "patch") {
                const patch = text(args.patchText) ?? text(args.patch);
                if (patch) return { command: ["spec", "guard", "--patch-stdin"], stdin: patch };
              } else if (tool === "bash" || tool === "shell") {
                const command = text(args.command);
                const workdir = text(args.workdir);
                const cwd = workdir ? (isAbsolute(workdir) ? workdir : join(directory, workdir)) : directory;
                if (command) return { command: ["spec", "guard", "--shell-stdin", "--cwd", cwd], stdin: command };
              } else {
                const skill = text(args.name) ?? text(args.skill);
                if (skill) return { command: ["spec", "guard", "--skill", skill] };
              }
              return { command: ["spec", "guard", "--unreadable-input"] };
            };

            export default Plugin.define({
              id: "ward.openspec-guard",
              setup: async (ctx) => {
                const run = async (args, input, directory) => {
                  try {
                    const proc = Bun.spawn(["ward", ...args], { cwd: directory, stdin: input === undefined ? "ignore" : new TextEncoder().encode(input), stdout: "pipe", stderr: "pipe" });
                    const [stdout, stderr, code] = await Promise.all([new Response(proc.stdout).text(), new Response(proc.stderr).text(), proc.exited]);
                    return { code, stdout, stderr };
                  } catch (error) {
                    return { code: null, stdout: "", stderr: "", error: String(error?.message ?? error) };
                  }
                };
                const before = await ctx.tool.hook("execute.before", async (event) => {
                  const tool = String(event.tool ?? "").toLowerCase();
                  if (!GUARDED.has(tool)) return;
                  let directory;
                  try {
                    directory = (await ctx.session.get({ sessionID: event.sessionID }))?.location?.directory;
                  } catch (error) {
                    return warning(`session location unavailable; proceeding: ${error?.message ?? error}`);
                  }
                  if (typeof directory !== "string" || !directory) return warning("session has no location; proceeding");
                  const args = event.input && typeof event.input === "object" ? event.input : {};
                  const { command, stdin } = guardCommand(tool, args, directory);
                  const result = await run(command, stdin, directory);
                  if (result.error) return warning(`Ward approved-artifact guard could not run; proceeding: ${result.error}`);
                  if (result.code === 1) throw new Error(result.stdout.trim().slice(0, 2000) || "Ward approved-artifact guard refused the tool call.");
                  if (result.code !== 0) warning(result.stderr.trim() || "Ward approved-artifact guard could not decide; proceeding.");
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
    return Files.isRegularFile(root.resolve(ROOT_FILES.commandFile()))
        && Files.isRegularFile(root.resolve(ROOT_FILES.pluginFile()));
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final boolean remove) {
    return integrate(projectRoot, remove ? IntegrationRequest.REMOVE : IntegrationRequest.INSTALL);
  }

  @Override
  public boolean installsAtRepositoryRoot() {
    return true;
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final OpenCodeFiles files = new OpenCodeFiles(request.projectPath());
    final Path command = root.resolve(files.commandFile());
    final Path plugin = root.resolve(files.pluginFile());
    final GuardFile guard =
        new GuardFile(root.resolve(files.guardFile()), GUARD_MARKER, files.guard(GUARD_PLUGIN));
    try {
      return request.remove()
          ? remove(command, plugin, guard)
          : install(command, plugin, guard, request.guard(), files);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update OpenCode integration: " + e.getMessage(), e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot, final GuardMode mode) {
    return plan(projectRoot, new IntegrationRequest(false, mode));
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final GuardMode mode = request.guard();
    final OpenCodeFiles files = new OpenCodeFiles(request.projectPath());
    final GuardFile guard =
        new GuardFile(root.resolve(files.guardFile()), GUARD_MARKER, files.guard(GUARD_PLUGIN));
    try {
      final List<PlannedFileChange> plan = new ArrayList<>();
      final Path command = root.resolve(files.commandFile());
      final Path plugin = root.resolve(files.pluginFile());
      plan.add(
          new PlannedFileChange(
              command, IntegrationArtifacts.decide(command, MARKER, files.command(COMMAND))));
      plan.add(
          new PlannedFileChange(
              plugin, IntegrationArtifacts.decide(plugin, PLUGIN_MARKER, files.plugin(PLUGIN))));
      if (guard.preflight(mode) != null) {
        plan.add(new PlannedFileChange(guard.file(), PlannedFileChange.Action.CONFLICT));
      }
      final List<PlannedFileChange> conflicts =
          plan.stream()
              .filter(change -> change.action() == PlannedFileChange.Action.CONFLICT)
              .toList();
      if (!conflicts.isEmpty()) {
        // Mirrors install: a conflict stops the run before anything is written.
        return conflicts;
      }
      guard.plan(mode).ifPresent(plan::add);
      return plan;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not plan OpenCode integration: " + e.getMessage(), e);
    }
  }

  private IntegrationResult install(
      final Path command,
      final Path plugin,
      final GuardFile guard,
      final GuardMode mode,
      final OpenCodeFiles files)
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
            command, MARKER, files.command(COMMAND), "Updated Ward OpenCode integration.");
    if (commandResult.status() == IntegrationResult.Status.CONFLICT) {
      return commandResult;
    }
    final IntegrationResult pluginResult =
        IntegrationArtifacts.installOrRefresh(
            plugin, PLUGIN_MARKER, files.plugin(PLUGIN), "Updated Ward OpenCode integration.");
    if (pluginResult.status() == IntegrationResult.Status.CONFLICT) {
      return pluginResult;
    }
    final List<Map.Entry<Path, IntegrationResult>> results =
        new ArrayList<>(
            List.of(Map.entry(command, commandResult), Map.entry(plugin, pluginResult)));
    if (guard.enabled(mode)) {
      results.add(guard.install("Updated Ward OpenCode approved-artifact guard."));
    }
    guard.remove(mode == GuardMode.DISABLE).ifPresent(results::add);
    return IntegrationArtifacts.combine(
        results,
        "Ward OpenCode integration is already present.",
        "Created Ward-owned OpenCode command and edit/write integration (or refreshed it).");
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
          IntegrationResult.Status.NOT_FOUND, List.of(), "No Ward OpenCode integration found.");
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
    EmptyDirectories.delete(plugin.getParent());
    EmptyDirectories.delete(command.getParent());
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED, removed, "Removed Ward-owned OpenCode integration.");
  }
}
