import { chmodSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import type { Fixture } from "../src/fixture";
import { sha256, type LiveContext, type LiveDriver } from "../src/live";

// Live Copilot CLI driver. `copilot -p` runs one non-interactive turn in the fixture; Copilot itself
// discovers .github/hooks/sentinel.json and dispatches the hooks. COPILOT_HOME is isolated by the
// fixture, so no user hooks, settings or plugins are loaded. Repository hooks load only from a
// trusted folder, so the fixture is listed in the isolated config's trusted_folders; that key is
// unconfirmed until a live run, and a run that never reaches Sentinel fails its invocation assertion.
// Source: https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-hooks-reference.

const PROTECTED = "openspec/specs/smoke/spec.md";
const ACTIVE = "openspec/changes/smoke-change/tasks.md";
const TURN_TIMEOUT_MS = 240_000;

function prepare(fixture: Fixture): void {
  for (const [path, text] of [
    [PROTECTED, "# Smoke spec\n\nApproved content.\n"],
    [ACTIVE, "# Tasks\n\n- [ ] 1.1 Smoke task\n"],
  ] as const) {
    mkdirSync(join(fixture.root, path, ".."), { recursive: true });
    writeFileSync(join(fixture.root, path), text);
  }
  // A trusted stand-in OpenSpec validation that reports a finding while SPEC_FAIL exists.
  writeFileSync(join(fixture.root, "openspec-stub.sh"), '#!/bin/sh\nif [ -f SPEC_FAIL ]; then echo "smoke-change: missing scenario"; exit 1; fi\n');
  chmodSync(join(fixture.root, "openspec-stub.sh"), 0o755);
  const toml = join(fixture.root, "sentinel.toml");
  writeFileSync(toml, `${readFileSync(toml, "utf8")}\n[quality-gates.openspec]\nenabled = true\ncommand = ["./openspec-stub.sh"]\n`);
  const home = fixture.env.COPILOT_HOME;
  mkdirSync(home, { recursive: true });
  writeFileSync(join(home, "config.json"), JSON.stringify({ trusted_folders: [fixture.root] }));
}

function turn(context: LiveContext, prompt: string) {
  return context.run([context.runtime.executable, "-p", prompt, "--allow-all-tools", "--no-color"], TURN_TIMEOUT_MS);
}

const guards = (context: LiveContext) => context.invocations().filter((i) => i.argv.includes("--guard-approved-specs"));
const checks = (context: LiveContext) =>
  context.invocations().filter((i) => i.argv[0] === "hook" && !i.argv.includes("--guard-approved-specs"));

export const copilotDriver: LiveDriver = {
  harness: "copilot",
  missingPrerequisites: (env) => (env.GH_TOKEN ? [] : ["GH_TOKEN"]),
  prepare,
  scenarios: [
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Create src/Smoke.java containing `class Smoke {}`. Then quote any hook feedback you received.");
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native PostToolUse hook invoked sentinel", passed: checks(context).length >= 1 },
          { name: "agent received the Sentinel failure", passed: result.stdout.includes("format") },
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
          { name: "native PreToolUse hook invoked the guard", passed: guards(context).length >= 1 },
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
    {
      id: "stop-feedback-limit",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "SPEC_FAIL"), "");
        const result = await turn(context, "Reply with the single word DONE and do nothing else.");
        const stops = checks(context);
        return [
          { name: "runtime turn completed", passed: !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "Stop hook invoked sentinel more than once (blocked, then continued)", passed: stops.length >= 2, detail: `${stops.length} invocation(s)` },
          { name: "the turn still ended within the deadline", passed: !result.timedOut },
        ];
      },
    },
  ],
};
