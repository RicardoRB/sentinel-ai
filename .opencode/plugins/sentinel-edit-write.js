// sentinel-owned: opencode-edit-write v1
import { Plugin } from "@opencode/plugin";

// Native OpenCode V2 quality and idle validation plugin (@opencode/plugin 2.0.30).
// Hooks: tool "execute.after" and the session.idle event. The host treats an
// execute.after rejection as a defect, so feedback is appended to the content the model
// reads instead of being thrown.
const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "apply_patch", "patch"]);
const CHECK = ["check", "--format", "json", "--exclude-spec", "--learn-after", "3"];
const FEEDBACK_MARKER = "[sentinel spec validation]";
const MAX_FEEDBACK = 20000;
const bound = (text) => (text.length > MAX_FEEDBACK ? `${text.slice(0, MAX_FEEDBACK)}\n[truncated]` : text);
const diagnostic = (message) => console.warn(`[sentinel] ${String(message).slice(0, 500)}`);
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
  id: "sentinel.edit-write",
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
        const proc = Bun.spawn(["sentinel", ...args], { cwd: directory, stdout: "pipe", stderr: "pipe", signal: abort.signal });
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
      if (check.error) return `Sentinel could not run the quality check: ${check.error}`;
      let report = null;
      try { report = JSON.parse(check.stdout); } catch { /* Non-JSON output is reported as text. */ }
      const learning = (report?.learning?.prompts ?? [])
        .map((prompt) => prompt?.instruction)
        .filter((instruction) => typeof instruction === "string" && instruction);
      const parts = [];
      if (check.code !== 0) parts.push(`Sentinel quality check failed:\n${bound((check.stdout || check.stderr).trim())}`);
      const specPaths = editedSpecPaths(paths);
      if (specPaths.length > 0) {
        const spec = await run(["spec", "validate", "--", ...specPaths], directory);
        if (spec.error) parts.push(`Sentinel could not run spec validation: ${spec.error}`);
        else if (spec.code !== 0) parts.push(`Sentinel spec validation failed:\n${bound((spec.stdout || spec.stderr).trim())}`);
      }
      return parts.concat(learning).join("\n");
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
        const findings = bound((result.stdout || result.stderr).trim() || "Sentinel spec validation reported findings.");
        if (state.generation !== generation) return diagnostic("dropping idle findings from a previous turn");
        if (state.delivered) return diagnostic(findings);
        state.delivered = true;
        await ctx.session.prompt({ sessionID, text: `${FEEDBACK_MARKER}\n${findings}`, metadata: { sentinel: FEEDBACK_MARKER }, delivery: "queue" });
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
      // Sentinel's own hand-off is not a new user turn.
      if (event.metadata?.sentinel === FEEDBACK_MARKER) return;
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
