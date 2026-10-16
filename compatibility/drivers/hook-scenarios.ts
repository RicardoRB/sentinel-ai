import { chmodSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Assertion } from "../src/evidence";
import type { Fixture } from "../src/fixture";
import { sha256, type LiveContext, type LiveScenario } from "../src/live";

// Live scenarios shared by harnesses that run Sentinel through a generated `sentinel.sh pre|post`
// wrapper: one headless turn per scenario, judged by recorded Sentinel invocations and file contents.

export const PROTECTED = "openspec/specs/smoke/spec.md";
export const ACTIVE = "openspec/changes/smoke-change/tasks.md";

/** Adds an approved spec, an active change and a stand-in OpenSpec gate that fails while SPEC_FAIL exists. */
export function prepareSmokeFixture(fixture: Fixture): void {
  for (const [path, text] of [
    [PROTECTED, "# Smoke spec\n\nApproved content.\n"],
    [ACTIVE, "# Tasks\n\n- [ ] 1.1 Smoke task\n"],
  ] as const) {
    mkdirSync(join(fixture.root, path, ".."), { recursive: true });
    writeFileSync(join(fixture.root, path), text);
  }
  writeFileSync(join(fixture.root, "openspec-stub.sh"), '#!/bin/sh\nif [ -f SPEC_FAIL ]; then echo "smoke-change: missing scenario"; exit 1; fi\n');
  chmodSync(join(fixture.root, "openspec-stub.sh"), 0o755);
  const toml = join(fixture.root, "sentinel.toml");
  writeFileSync(toml, `${readFileSync(toml, "utf8")}\n[quality-gates.openspec]\nenabled = true\ncommand = ["./openspec-stub.sh"]\n`);
}

type Turn = (context: LiveContext, prompt: string) => ReturnType<LiveContext["run"]>;

const guards = (context: LiveContext) => context.invocations().filter((i) => i.argv.includes("--guard-approved-specs"));
const checks = (context: LiveContext) =>
  context.invocations().filter((i) => i.argv[0] === "hook" && !i.argv.includes("--guard-approved-specs"));

/** Edit feedback, guard refusal, permitted active-change edit and the turn-end continuation limit. */
export function hookScenarios(turn: Turn): LiveScenario[] {
  return [
    {
      id: "edit-check-feedback",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "FAIL"), "");
        const result = await turn(context, "Create src/Smoke.java containing `class Smoke {}`. Then quote any hook feedback you received.");
        return [
          { name: "runtime turn completed", passed: result.exitCode === 0 && !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "native post-edit hook invoked sentinel", passed: checks(context).length >= 1 },
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
          { name: "native pre-tool hook invoked the guard", passed: guards(context).length >= 1 },
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
      id: "turn-end-feedback-limit",
      async execute(context): Promise<Assertion[]> {
        writeFileSync(join(context.fixture.root, "SPEC_FAIL"), "");
        const result = await turn(context, "Reply with the single word DONE and do nothing else.");
        const ends = checks(context);
        return [
          { name: "runtime turn completed", passed: !result.timedOut, detail: `exit ${result.exitCode}` },
          { name: "turn-end hook invoked sentinel more than once (blocked, then continued)", passed: ends.length >= 2, detail: `${ends.length} invocation(s)` },
          { name: "the turn still ended within the deadline", passed: !result.timedOut },
        ];
      },
    },
  ];
}
