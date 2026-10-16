import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import type { Fixture } from "../src/fixture";
import { sha256, type LiveContext, type LiveDriver } from "../src/live";

// Live Amp driver. Amp loads project plugins from .amp/plugins only when PLUGINS=all is set, and
// only in the binary-installed CLI. Execute mode (-x) runs one agent turn; --plugin-ready-timeout
// waits for plugins before the turn so tool.call/tool.result/agent.end handlers are active.
// Sources: https://ampcode.com/manual/plugin-api, https://ampcode.com/docs/cli/execute-mode.

const PROTECTED = "openspec/specs/smoke/spec.md";
const ACTIVE = "openspec/changes/smoke-change/tasks.md";
const TURN_TIMEOUT_MS = 240_000;
const CHECK = ["hook", "native", "--agent", "amp", "--event-stdin"];
const GUARD = ["hook", "native", "--agent", "amp", "--guard-approved-specs", "--event-stdin"];
const same = (a: string[], b: string[]) => JSON.stringify(a) === JSON.stringify(b);

function prepare(fixture: Fixture): void {
  for (const [path, text] of [
    [PROTECTED, "# Smoke spec\n\nApproved content.\n"],
    [ACTIVE, "# Tasks\n\n- [ ] 1.1 Smoke task\n"],
  ] as const) {
    mkdirSync(join(fixture.root, path, ".."), { recursive: true });
    writeFileSync(join(fixture.root, path), text);
  }
}

function turn(context: LiveContext, prompt: string) {
  return context.run(
    ["/usr/bin/env", "PLUGINS=all", context.runtime.executable, "--plugin-ready-timeout", "30", "-x", prompt],
    TURN_TIMEOUT_MS,
  );
}

export const ampDriver: LiveDriver = {
  harness: "amp",
  missingPrerequisites: (env) => (env.AMP_API_KEY ? [] : ["AMP_API_KEY"]),
  prepare,
  scenarios: [
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Create the file src/Smoke.java containing `class Smoke {}`. Then report any tool output you received verbatim.");
        const checks = context.invocations().filter((i) => same(i.argv, CHECK));
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native tool.result invoked sentinel", passed: checks.length >= 1, detail: `${checks.length} check invocation(s)` },
          { name: "agent reported the Sentinel failure it received", passed: result.stdout.includes("Sentinel") },
        ];
      },
    },
    {
      id: "guard-refuses-protected-edit",
      async execute(context): Promise<Assertion[]> {
        const path = join(context.fixture.root, PROTECTED);
        const before = sha256(path);
        const result = await turn(context, `Replace "Approved content." with "Changed." in ${PROTECTED}.`);
        return [
          { name: "runtime turn completed", passed: !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native tool.call invoked the guard", passed: context.invocations().some((i) => same(i.argv, GUARD)) },
          { name: "protected file is unchanged", passed: sha256(path) === before },
        ];
      },
    },
    {
      id: "guard-allows-active-change-edit",
      async execute(context): Promise<Assertion[]> {
        const result = await turn(context, `In ${ACTIVE}, replace "- [ ] 1.1" with "- [x] 1.1".`);
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "permitted active-change edit executed", passed: readFileSync(join(context.fixture.root, ACTIVE), "utf8").includes("- [x] 1.1") },
        ];
      },
    },
    { id: "turn-end-continuation", notImplemented: "needs an OpenSpec-enabled trusted fixture to produce turn-end findings" },
  ],
};
