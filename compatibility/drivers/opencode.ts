import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import type { Fixture } from "../src/fixture";
import { sha256, type LiveContext, type LiveDriver } from "../src/live";

// Live OpenCode V2 driver. It launches the pinned runtime with a private standalone server in the
// fixture, so the runtime itself discovers .opencode/plugins and dispatches native tool events.
// A model turn is required to produce tool calls; the model is named explicitly and credentials
// come only from the allowlist.

const PROTECTED = "openspec/specs/smoke/spec.md";
const ACTIVE = "openspec/changes/smoke-change/tasks.md";
const TURN_TIMEOUT_MS = 240_000;

function prepare(fixture: Fixture): void {
  for (const [path, text] of [
    [PROTECTED, "# Smoke spec\n\nApproved content.\n"],
    [ACTIVE, "# Tasks\n\n- [ ] 1.1 Smoke task\n"],
    ["openspec/changes/smoke-change/proposal.md", "# Proposal\n\nSmoke.\n"],
  ] as const) {
    mkdirSync(join(fixture.root, path, ".."), { recursive: true });
    writeFileSync(join(fixture.root, path), text);
  }
}

async function turn(context: LiveContext, prompt: string) {
  const model = process.env.SENTINEL_COMPAT_OPENCODE_MODEL as string;
  return context.run(
    [context.runtime.executable, "run", "--standalone", "--format", "json", "--auto", "--model", model, "--print-logs", "--log-level", "info", prompt],
    TURN_TIMEOUT_MS,
  );
}

const CHECK = ["check", "--format", "json", "--exclude-spec", "--learn-after", "3"];
const sameArgv = (a: string[], b: string[]) => JSON.stringify(a) === JSON.stringify(b);

export const opencodeDriver: LiveDriver = {
  harness: "opencode",
  missingPrerequisites(env) {
    const missing: string[] = [];
    if (!env.SENTINEL_COMPAT_OPENCODE_MODEL) missing.push("SENTINEL_COMPAT_OPENCODE_MODEL (provider/model)");
    if (!["ANTHROPIC_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY", "GEMINI_API_KEY"].some((name) => env[name])) {
      missing.push("a model provider API key from the credential allowlist");
    }
    return missing;
  },
  prepare,
  scenarios: [
    {
      // Model-free: the runtime discovers and loads project plugins before any model call, and an
      // unroutable model ends the turn without contacting a provider.
      id: "plugin-activation",
      requiresCredentials: false,
      async execute(context): Promise<Assertion[]> {
        const result = await context.run(
          [context.runtime.executable, "run", "--standalone", "--format", "json", "--model", "sentinel-compat/unroutable", "--print-logs", "--log-level", "info", "activation probe"],
          120_000,
        );
        const lines = result.stderr.split("\n");
        const loading = (file: string) => lines.some((l) => l.includes('msg="loading plugin"') && l.includes(`/.opencode/plugins/${file}`));
        const failed = (file: string) => lines.some((l) => l.includes('message="failed to load plugin"') && l.includes(`/.opencode/plugins/${file}`));
        return [
          { name: "runtime exited within the deadline", passed: !result.timedOut },
          { name: "runtime discovered sentinel-edit-write.js", passed: loading("sentinel-edit-write.js") },
          { name: "runtime discovered sentinel-openspec-guard.js", passed: loading("sentinel-openspec-guard.js") },
          { name: "sentinel-edit-write.js loaded without error", passed: loading("sentinel-edit-write.js") && !failed("sentinel-edit-write.js") },
          { name: "sentinel-openspec-guard.js loaded without error", passed: loading("sentinel-openspec-guard.js") && !failed("sentinel-openspec-guard.js") },
        ];
      },
    },
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        // The fixture's only gate runs the stub mvnw; a FAIL file makes it fail deterministically.
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Use the write tool to create the file src/Smoke.java containing `class Smoke {}`. Then stop.");
        const checks = context.invocations().filter((i) => sameArgv(i.argv, CHECK));
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native edit event invoked sentinel check", passed: checks.length >= 1, detail: `${checks.length} check invocation(s)` },
          { name: "check ran in the session location", passed: checks.every((i) => i.cwd.endsWith("/project")) },
          { name: "agent-visible tool result carries the failure", passed: result.stdout.includes("Sentinel quality check failed") },
        ];
      },
    },
    {
      id: "guard-refuses-protected-edit",
      async execute(context): Promise<Assertion[]> {
        const path = join(context.fixture.root, PROTECTED);
        const before = sha256(path);
        const result = await turn(context, `Use the edit tool to replace "Approved content." with "Changed." in ${PROTECTED}. Then stop.`);
        const guards = context.invocations().filter((i) => i.argv[0] === "spec" && i.argv[1] === "guard");
        return [
          { name: "runtime turn completed", passed: !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native pre-tool event invoked sentinel spec guard", passed: guards.length >= 1 },
          { name: "protected file is unchanged", passed: sha256(path) === before },
        ];
      },
    },
    {
      id: "guard-allows-active-change-edit",
      async execute(context): Promise<Assertion[]> {
        const path = join(context.fixture.root, ACTIVE);
        const result = await turn(context, `Use the edit tool to replace "- [ ] 1.1" with "- [x] 1.1" in ${ACTIVE}. Then stop.`);
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "permitted active-change edit executed", passed: readFileSync(path, "utf8").includes("- [x] 1.1") },
        ];
      },
    },
    { id: "idle-feedback-limit", notImplemented: "needs an OpenSpec-enabled trusted fixture and a multi-turn session driver" },
    { id: "reload-cleanup", notImplemented: "needs a long-lived session that can reload plugins between turns" },
  ],
};
