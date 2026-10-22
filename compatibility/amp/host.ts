/**
 * Minimal Amp plugin host for executing Ward's generated .amp/plugins/ward.ts against the
 * pinned @ampcode/plugin 0.0.0-20261016001627-gab25fd2 contract (index.d.ts):
 *
 * - `amp.on(event, handler)` registers handlers; handlers receive `(event, ctx)`.
 * - `tool.call` must return `{ action: "allow" | "reject-and-continue" | ... }`.
 * - `tool.result` may return `{ status, output }` to replace what the model reads, or nothing.
 * - `agent.end` may return `{ action: "continue", userMessage, maxContinuations? }`.
 * - `system.workspaceRoot` is a file URI; `helpers.filePathFromURI` converts it.
 *
 * The edit and shell helpers are emulated from fixed tool tables: the real helpers live in the Amp
 * runtime, so this host proves wiring, not Amp's own tool classification.
 */
type Handler = (event: any, ctx: any) => unknown;

export interface ToolEvent {
  tool: string;
  input: Record<string, unknown>;
  toolUseID?: string;
}

const EDIT_TOOLS: Record<string, string> = { edit_file: "path", create_file: "path" };
const SHELL_TOOLS = new Set(["Bash"]);

export class AmpHost {
  readonly handlers = new Map<string, Handler[]>();
  readonly logs: string[] = [];

  constructor(readonly root: string | null) {}

  api(): unknown {
    const uri = (path: string) => ({ toString: () => `file://${path}`, path });
    return {
      system: { workspaceRoot: this.root ? uri(this.root) : null },
      logger: { log: (...args: unknown[]) => this.logs.push(args.join(" ")) },
      helpers: {
        filePathFromURI: (value: { path: string }) => value.path,
        filesModifiedByToolCall: (event: ToolEvent) => {
          const key = EDIT_TOOLS[event.tool];
          const path = key ? event.input[key] : undefined;
          if (typeof path !== "string") return null;
          return [uri(path.startsWith("/") ? path : `${this.root}/${path}`)];
        },
        shellCommandFromToolCall: (event: ToolEvent) =>
          SHELL_TOOLS.has(event.tool) && typeof event.input.cmd === "string"
            ? { command: event.input.cmd, ...(typeof event.input.cwd === "string" ? { dir: event.input.cwd } : {}) }
            : null,
      },
      on: (name: string, handler: Handler) => {
        this.handlers.set(name, [...(this.handlers.get(name) ?? []), handler]);
        return { unsubscribe: () => this.handlers.set(name, (this.handlers.get(name) ?? []).filter((h) => h !== handler)) };
      },
    };
  }

  private context() {
    return { logger: { log: (...args: unknown[]) => this.logs.push(args.join(" ")) }, thread: { id: "T-1" } };
  }

  private async dispatch(name: string, event: unknown): Promise<unknown[]> {
    const results: unknown[] = [];
    for (const handler of this.handlers.get(name) ?? []) results.push(await handler(event, this.context()));
    return results;
  }

  async toolCall(event: ToolEvent): Promise<unknown> {
    const results = await this.dispatch("tool.call", { toolUseID: "toolu_1", thread: { id: "T-1" }, ...event });
    if (results.length === 0) return { action: "allow" };
    for (const result of results) {
      const action = (result as { action?: string })?.action;
      if (!["allow", "reject-and-continue", "modify", "synthesize", "error"].includes(String(action))) {
        throw new Error(`tool.call handler returned an invalid result: ${JSON.stringify(result)}`);
      }
    }
    return results.find((r) => (r as { action: string }).action !== "allow") ?? { action: "allow" };
  }

  /** Returns the output the model reads after every tool.result handler ran. */
  async toolResult(event: ToolEvent & { status: "done" | "error" | "cancelled"; output?: unknown; error?: string }) {
    let current: { status: string; output?: unknown; error?: string } = { status: event.status, output: event.output, error: event.error };
    for (const result of await this.dispatch("tool.result", { toolUseID: "toolu_1", thread: { id: "T-1" }, ...event })) {
      if (result === undefined) continue;
      const status = (result as { status?: string }).status;
      if (!["done", "error", "cancelled"].includes(String(status))) throw new Error(`invalid tool.result result: ${JSON.stringify(result)}`);
      current = result as typeof current;
    }
    return current;
  }

  async agentEnd(status: "done" | "error" | "cancelled" = "done"): Promise<unknown[]> {
    const results = await this.dispatch("agent.end", { thread: { id: "T-1" }, message: "do it", id: 1, status, messages: [] });
    return results.filter((result) => result !== undefined);
  }
}
