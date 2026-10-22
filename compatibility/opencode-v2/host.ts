/**
 * Minimal host for executing Ward's generated OpenCode V2 plugins against the pinned
 * @opencode/plugin 2.0.30 contract. Semantics mirror opencode v2.0.30:
 *
 * - packages/plugin/src/promise/adapter.ts: an `execute.before` rejection becomes a Tool.Error that
 *   refuses that call alone; an `execute.after` rejection is a defect (Effect.promise), not
 *   feedback.
 * - packages/core/src/tool.ts + tool/runtime.ts: the model sees `normalizeContent(result.content,
 *   result.output)` after every `execute.after` callback ran.
 * - Event envelopes carry `type`, `data` and optional `location` (session.idle has
 *   `data.sessionID`); `ctx.session.get` returns SessionInfo with `location.directory`.
 */
export interface ToolResult {
  output?: unknown;
  content?: string | ReadonlyArray<{ type: "text"; text: string }>;
  metadata?: Record<string, unknown>;
}

export type Content = { type: "text"; text: string }[];

export class Refusal extends Error {}
export class Defect extends Error {}

type Callback = (event: any) => unknown;

export interface Prompt {
  sessionID: string;
  text: string;
  metadata?: Record<string, unknown>;
  delivery?: string;
}

export class Host {
  readonly toolHooks = new Map<string, Callback[]>();
  readonly sessionHooks = new Map<string, Callback[]>();
  readonly prompts: Prompt[] = [];
  readonly sessions = new Map<string, string>();
  sessionLookupFails = false;
  subscriptions = 0;
  private listeners: ((event: unknown) => void)[] = [];

  constructor(readonly directory: string) {}

  context(): unknown {
    const register = (map: Map<string, Callback[]>) => async (name: string, callback: Callback) => {
      map.set(name, [...(map.get(name) ?? []), callback]);
      let active = true;
      return {
        dispose: async () => {
          if (!active) return;
          active = false;
          map.set(name, (map.get(name) ?? []).filter((entry) => entry !== callback));
        },
      };
    };
    return {
      location: { directory: this.directory },
      tool: { hook: register(this.toolHooks) },
      session: {
        hook: register(this.sessionHooks),
        get: async ({ sessionID }: { sessionID: string }) => {
          const directory = this.sessions.get(sessionID);
          if (this.sessionLookupFails || !directory) throw new Error(`session ${sessionID} not found`);
          return { id: sessionID, location: { directory } };
        },
        prompt: async (input: Prompt) => {
          if (typeof input?.text !== "string") throw new Error("session.prompt requires text");
          if (input.delivery !== undefined && input.delivery !== "steer" && input.delivery !== "queue") {
            throw new Error(`invalid delivery ${input.delivery}`);
          }
          this.prompts.push(input);
          // Prompts submitted through session.prompt run the prompt hook (docs: build/plugins).
          await this.runSessionHook("prompt", {
            sessionID: input.sessionID,
            prompt: { text: input.text },
            metadata: input.metadata,
            delivery: input.delivery ?? "queue",
          });
          return { id: `prompt-${this.prompts.length}` };
        },
      },
      event: {
        subscribe: (options?: { signal?: AbortSignal }) => this.stream(options?.signal),
      },
    };
  }

  private stream(signal?: AbortSignal): AsyncIterable<unknown> {
    const host = this;
    return {
      [Symbol.asyncIterator]() {
        const queue: unknown[] = [];
        let wake: (() => void) | undefined;
        const listener = (event: unknown) => {
          queue.push(event);
          wake?.();
        };
        host.listeners.push(listener);
        host.subscriptions++;
        const stop = () => {
          host.listeners = host.listeners.filter((entry) => entry !== listener);
          wake?.();
        };
        signal?.addEventListener("abort", stop, { once: true });
        return {
          async next() {
            while (queue.length === 0) {
              if (signal?.aborted || !host.listeners.includes(listener)) return { done: true, value: undefined };
              await new Promise<void>((resolve) => (wake = resolve));
              wake = undefined;
            }
            return { done: false, value: queue.shift() };
          },
          async return() {
            stop();
            return { done: true, value: undefined };
          },
        };
      },
    };
  }

  emit(event: unknown): void {
    for (const listener of this.listeners) listener(event);
  }

  idle(sessionID: string, directory = this.sessions.get(sessionID)): void {
    this.emit({
      id: `evt-${Math.random()}`,
      created: Date.now(),
      type: "session.idle",
      ...(directory ? { location: { directory } } : {}),
      data: { sessionID },
    });
  }

  async runSessionHook(name: string, event: unknown): Promise<void> {
    for (const callback of this.sessionHooks.get(name) ?? []) await callback(event);
  }

  /** Runs `execute.before` like the host: any rejection refuses the call. */
  async before(event: { tool: string; sessionID: string; input: unknown }): Promise<void> {
    for (const callback of this.toolHooks.get("execute.before") ?? []) {
      try {
        await callback({ agent: "build", messageID: "m1", id: "call-1", ...event });
      } catch (error) {
        throw new Refusal(error instanceof Error ? error.message : String(error));
      }
    }
  }

  /** Runs `execute.after` like the host and returns what the model would see. */
  async after(event: {
    tool: string;
    sessionID: string;
    input: unknown;
    status?: "completed" | "error";
    result?: ToolResult;
    error?: { message: string };
  }): Promise<{ event: any; content?: Content }> {
    const full: any = { agent: "build", messageID: "m1", id: "call-1", status: "completed", ...event };
    for (const callback of this.toolHooks.get("execute.after") ?? []) {
      try {
        await callback(full);
      } catch (error) {
        throw new Defect(`execute.after rejected: ${error instanceof Error ? error.message : String(error)}`);
      }
    }
    if (full.status !== "completed") return { event: full };
    return { event: full, content: normalizeContent(full.result?.content, full.result?.output) };
  }
}

export function normalizeContent(value: ToolResult["content"], output?: unknown): Content {
  if (typeof value === "string") return [{ type: "text", text: value }];
  if (value !== undefined && value.length > 0) return [...value] as Content;
  return [{ type: "text", text: typeof output === "string" ? output : (JSON.stringify(output) ?? String(output)) }];
}
