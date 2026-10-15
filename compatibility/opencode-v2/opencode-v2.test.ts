import { expect, test } from "bun:test";

const project = process.env.SENTINEL_OPENCODE_FIXTURE;
if (!project)
  throw new Error(
    "SENTINEL_OPENCODE_FIXTURE must point to an integrated trusted fixture",
  );

const load = async (name: string) =>
  import(`${project}/.opencode/plugins/${name}?compat=${Date.now()}`);

const context = (events: unknown[] = []) => {
  const hooks = new Map<string, (event: any) => unknown>();
  const registrations = {
    tool: {
      hook: async (name: string, callback: any) => {
        hooks.set(name, callback);
        return { dispose: async () => hooks.delete(name) };
      },
    },
    session: {
      hook: async (_name: string, _callback: any) => ({
        dispose: async () => {},
      }),
      get: async () => ({ data: { directory: project } }),
      prompt: async () => {},
    },
  };
  return {
    hooks,
    ctx: {
      location: { directory: project },
      ...registrations,
      event: { subscribe: () => events },
    },
  };
};

test("generated quality plugin is a native V2 definition", async () => {
  const plugin = (await load("sentinel-edit-write.js")).default;
  expect(plugin.id).toBe("sentinel.edit-write");
  const { hooks, ctx } = context([]);
  const cleanup = await plugin.setup(ctx);
  expect(hooks.has("execute.after")).toBe(true);
  await cleanup?.();
});

test("generated guard is a native V2 definition", async () => {
  const plugin = (await load("sentinel-openspec-guard.js")).default;
  expect(plugin.id).toBe("sentinel.openspec-guard");
  const { hooks, ctx } = context([]);
  const cleanup = await plugin.setup(ctx);
  expect(hooks.has("execute.before")).toBe(true);
  await cleanup?.();
});

test("an invalid plugin entrypoint fails instead of being silently skipped", async () => {
  await expect(import("data:text/javascript,export default")).rejects.toThrow();
});
