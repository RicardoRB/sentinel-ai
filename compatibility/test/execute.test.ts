import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import { realpathSync } from "node:fs";
import { join } from "node:path";
import { Assertions, disconnectWrapper, executionHarness, InvalidRegistration, registrations } from "../src/execute";
import { createFixture, sentinelLauncher, type Fixture } from "../src/fixture";
import { cleanupOwnedProcesses } from "../src/process";

describe("documented registration shapes", () => {
  test("matcher groups resolve every command handler", () => {
    const json = JSON.stringify({
      hooks: { PreToolUse: [{ matcher: "Bash", hooks: [{ type: "command", command: "a.sh" }] }], Stop: [{ hooks: [{ type: "command", command: "b.sh" }] }] },
    });
    expect(registrations(json, "matcher-groups")).toEqual([
      { event: "PreToolUse", command: "a.sh", matcher: "Bash" },
      { event: "Stop", command: "b.sh" },
    ]);
  });

  test("a flat entry where a matcher group is required is an invalid registration", () => {
    const json = JSON.stringify({ hooks: { PostToolUse: [{ type: "command", command: "a.sh" }] } });
    expect(() => registrations(json, "matcher-groups")).toThrow(new InvalidRegistration("PostToolUse[0] must be a matcher group with a hooks list"));
  });

  test("versioned command configurations require version 1 and flat commands", () => {
    expect(registrations(JSON.stringify({ version: 1, hooks: { stop: [{ command: "s.sh" }] } }), "versioned-commands")).toEqual([
      { event: "stop", command: "s.sh" },
    ]);
    expect(() => registrations(JSON.stringify({ hooks: { stop: [{ command: "s.sh" }] } }), "versioned-commands")).toThrow(InvalidRegistration);
    expect(() =>
      registrations(JSON.stringify({ version: 1, hooks: { stop: [{ hooks: [{ type: "command", command: "s.sh" }] }] } }), "versioned-commands"),
    ).toThrow(InvalidRegistration);
  });

  test("Copilot entries need a bash command", () => {
    expect(registrations(JSON.stringify({ version: 1, hooks: { postToolUse: [{ type: "command", bash: "h.sh" }] } }), "copilot")).toEqual([
      { event: "postToolUse", command: "h.sh" },
    ]);
    expect(() => registrations(JSON.stringify({ version: 1, hooks: { postToolUse: [{ type: "command", command: "h.sh" }] } }), "copilot")).toThrow(
      InvalidRegistration,
    );
  });

  test("unparseable or empty configurations are invalid", () => {
    expect(() => registrations("{", "commands")).toThrow(new InvalidRegistration("configuration is not valid JSON"));
    expect(() => registrations("{}", "commands")).toThrow(new InvalidRegistration("configuration has no hooks object"));
    expect(() => registrations(JSON.stringify({ hooks: { e: {} } }), "commands")).toThrow(new InvalidRegistration("e must be a list"));
  });
});

describe("generated artifact execution", () => {
  let fixture: Fixture;
  beforeAll(async () => {
    fixture = await createFixture({ harness: "codex", guard: false, sentinel: sentinelLauncher(process.env)! });
  });
  afterAll(() => {
    fixture?.cleanup();
    cleanupOwnedProcesses();
  });

  test("records argv, stdin and cwd that reach Sentinel from the generated wrapper", async () => {
    const harness = executionHarness(fixture);
    const payload = JSON.stringify({ hook_event_name: "PostToolUse", tool_name: "apply_patch", cwd: fixture.root });
    const { result, invocations } = await harness.execute(".codex/hooks/sentinel.sh post", { cwd: fixture.root, stdin: payload });
    const assertions = new Assertions();
    assertions.invokedSentinel(invocations, ["hook", "native", "--agent", "codex", "--event-stdin"]);
    assertions.check("payload passed through stdin", invocations[0]?.stdin === payload);
    assertions.check("runs in the session cwd", invocations[0]?.cwd === realpathSync(fixture.root));
    assertions.check("exit status preserved", result.exitCode === 0);
    expect(assertions.list.filter((a) => !a.passed)).toEqual([]);
    expect(assertions.passed).toBe(true);
  });

  test("stub exit status, stdout and stderr propagate through the wrapper", async () => {
    const harness = executionHarness(fixture);
    const { result } = await harness.execute(".codex/hooks/sentinel.sh post", {
      cwd: fixture.root,
      stdin: "{}",
      stub: { exitCode: 1, stdout: "out", stderr: "err" },
    });
    expect([result.exitCode, result.stdout, result.stderr]).toEqual([1, "out", "err"]);
  });

  test("negative control: a disconnected wrapper fails the invocation assertion", async () => {
    const harness = executionHarness(fixture);
    const disconnected = await createFixture({ harness: "codex", guard: false, sentinel: fixture.sentinel });
    try {
      disconnectWrapper(join(disconnected.root, ".codex/hooks/sentinel.sh"));
      const { result, invocations } = await harness.execute(".codex/hooks/sentinel.sh post", { cwd: disconnected.root, stdin: "{}" });
      const assertions = new Assertions();
      expect(result.exitCode).toBe(0);
      expect(assertions.invokedSentinel(invocations, ["hook", "native", "--agent", "codex", "--event-stdin"])).toBe(false);
      expect(assertions.passed).toBe(false);
    } finally {
      disconnected.cleanup();
    }
  });

  test("an assertion list with nothing executed is not a pass", () => {
    expect(new Assertions().passed).toBe(false);
  });
});
