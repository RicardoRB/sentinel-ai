import { afterAll, describe, expect, test } from "bun:test";
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DRIVERS } from "../drivers";
import type { LiveDriver } from "../src/live";
import { runLive } from "../src/live";
import { loadManifest, REPO_ROOT } from "../src/manifest";
import { cleanupOwnedProcesses } from "../src/process";

const base = mkdtempSync(join(tmpdir(), "sentinel-live-"));
const home = join(base, "home");
mkdirSync(home);
afterAll(() => {
  rmSync(base, { recursive: true, force: true });
  cleanupOwnedProcesses();
});

/** A stand-in runtime that only answers its version command; no harness is launched. */
function fakeRuntime(name: string, versionLine: string): string {
  const path = join(base, name);
  writeFileSync(path, `#!/bin/sh\necho "${versionLine}"\n`);
  chmodSync(path, 0o755);
  return path;
}

const options = (env: Record<string, string>, drivers: Record<string, LiveDriver> = DRIVERS) => ({
  env: { ...env, JAVA_HOME: process.env.JAVA_HOME },
  toolsDir: join(base, "no-tools"),
  home,
  drivers,
  revision: "rev-under-test",
});

describe("live verification prerequisites", () => {
  test("a harness without a live driver is blocked as unimplemented", async () => {
    const report = await runLive(["cline"], options({}, {}));
    expect(report.exitCode).toBe(1);
    expect(report.harnesses[0].outcome).toBe("blocked");
    expect(report.harnesses[0].scenarios[0].reason).toBe("verification driver is not implemented");
  });

  test("Cline is blocked on its provider settings, and its turn-end scenario is unsupported", async () => {
    const runtime = fakeRuntime("cline-pinned", "3.0.72");
    const report = await runLive(["cline"], options({ SENTINEL_COMPAT_CLINE_BIN: runtime }));
    const reasons = report.harnesses[0].scenarios.map((s) => [s.scenario, s.outcome]);
    expect(reasons).toContainEqual(["turn-end-feedback-limit", "unsupported"]);
    expect(report.harnesses[0].scenarios.filter((s) => s.outcome === "blocked")[0].reason).toStartWith(
      "missing prerequisite: SENTINEL_COMPAT_CLINE_PROVIDER",
    );
    expect(report.exitCode).toBe(1);
  });

  test("a missing runtime blocks every scenario and names it", async () => {
    const report = await runLive(["opencode"], options({}));
    expect(report.harnesses[0].outcome).toBe("blocked");
    for (const scenario of report.harnesses[0].scenarios) {
      expect(scenario.reason).toBe(
        "missing prerequisite: opencode 2.0.30 (set SENTINEL_COMPAT_OPENCODE_BIN to an absolute executable path)",
      );
    }
  });

  test("a runtime version mismatch is blocked", async () => {
    const runtime = fakeRuntime("opencode-old", "opencode v2.0.29");
    const report = await runLive(["opencode"], options({ SENTINEL_COMPAT_OPENCODE_BIN: runtime }));
    expect(report.harnesses[0].scenarios[0].reason).toBe("missing prerequisite: version mismatch: expected 2.0.30, found 2.0.29");
  });

  test("missing model credentials block model-backed scenarios, and a fake runtime cannot pass activation", async () => {
    const runtime = fakeRuntime("opencode-ok", "opencode v2.0.30");
    const report = await runLive(["opencode"], options({ SENTINEL_COMPAT_OPENCODE_BIN: runtime }));
    expect(report.exitCode).toBe(1);
    const [activation, ...rest] = report.harnesses[0].scenarios;
    expect(activation).toMatchObject({ scenario: "plugin-activation", outcome: "fail" });
    for (const scenario of rest.filter((s) => s.reason.startsWith("missing prerequisite"))) {
      expect(scenario.reason).toBe(
        "missing prerequisite: SENTINEL_COMPAT_OPENCODE_MODEL (provider/model), a model provider API key from the credential allowlist",
      );
    }
    expect(rest.map((s) => s.outcome)).toEqual(["blocked", "blocked", "blocked", "blocked", "blocked"]);
  });

  test("Amp is blocked on its pinned binary and then on AMP_API_KEY", async () => {
    const missing = await runLive(["amp"], options({}));
    expect(missing.harnesses[0].scenarios[0].reason).toBe(
      "missing prerequisite: amp 0.0.1792123242-g03edc7 (set SENTINEL_COMPAT_AMP_BIN to an absolute executable path)",
    );
    const runtime = fakeRuntime("amp-pinned", "0.0.1792123242-g03edc7");
    const report = await runLive(["amp"], options({ SENTINEL_COMPAT_AMP_BIN: runtime }));
    expect(report.harnesses[0].scenarios.map((s) => s.reason)).toEqual([
      "missing prerequisite: AMP_API_KEY",
      "missing prerequisite: AMP_API_KEY",
      "missing prerequisite: AMP_API_KEY",
      "verification driver is not implemented",
    ]);
    expect(report.exitCode).toBe(1);
  });

  test("Claude Code is blocked on ANTHROPIC_API_KEY once its pinned runtime is present", async () => {
    const runtime = fakeRuntime("claude-pinned", "2.1.292 (Claude Code)");
    const report = await runLive(["claude-code"], options({ SENTINEL_COMPAT_CLAUDE_CODE_BIN: runtime }));
    expect(new Set(report.harnesses[0].scenarios.map((s) => s.reason))).toEqual(new Set(["missing prerequisite: ANTHROPIC_API_KEY"]));
    expect(report.harnesses[0].scenarios.length).toBe(4);
    expect(report.exitCode).toBe(1);
  });

  test("Copilot CLI is blocked on GH_TOKEN once its pinned runtime is present", async () => {
    const missing = await runLive(["copilot"], options({}));
    expect(missing.harnesses[0].scenarios[0].reason).toStartWith("missing prerequisite: copilot 1.0.98");
    const runtime = fakeRuntime("copilot-pinned", "1.0.98");
    const report = await runLive(["copilot"], options({ SENTINEL_COMPAT_COPILOT_BIN: runtime }));
    expect(new Set(report.harnesses[0].scenarios.map((s) => s.reason))).toEqual(new Set(["missing prerequisite: GH_TOKEN"]));
    expect(report.harnesses[0].scenarios.length).toBe(4);
    expect(report.exitCode).toBe(1);
  });

  test("Gemini CLI is blocked on GEMINI_API_KEY once its pinned runtime is present", async () => {
    const runtime = fakeRuntime("gemini-pinned", "0.64.0");
    const report = await runLive(["gemini"], options({ SENTINEL_COMPAT_GEMINI_BIN: runtime }));
    expect(new Set(report.harnesses[0].scenarios.map((s) => s.reason))).toEqual(new Set(["missing prerequisite: GEMINI_API_KEY"]));
    expect(report.harnesses[0].scenarios.length).toBe(4);
    expect(report.exitCode).toBe(1);
  });

  test("Factory Droid is blocked on FACTORY_API_KEY once its pinned runtime is present", async () => {
    const runtime = fakeRuntime("droid-pinned", "0.243.0");
    const report = await runLive(["factory-droid"], options({ SENTINEL_COMPAT_FACTORY_DROID_BIN: runtime }));
    expect(new Set(report.harnesses[0].scenarios.map((s) => s.reason))).toEqual(new Set(["missing prerequisite: FACTORY_API_KEY"]));
    expect(report.harnesses[0].scenarios.length).toBe(4);
    expect(report.exitCode).toBe(1);
  });

  test("Cursor is blocked on CURSOR_API_KEY once its pinned runtime is present", async () => {
    const runtime = fakeRuntime("cursor-pinned", "2026.10.14-941492e");
    const report = await runLive(["cursor"], options({ SENTINEL_COMPAT_CURSOR_BIN: runtime }));
    expect(new Set(report.harnesses[0].scenarios.map((s) => s.reason))).toEqual(new Set(["missing prerequisite: CURSOR_API_KEY"]));
    expect(report.harnesses[0].scenarios.length).toBe(4);
    expect(report.exitCode).toBe(1);
  });

  test("editor-only harnesses are blocked pending a witnessed procedure", async () => {
    const witnessed: LiveDriver = { harness: "windsurf", missingPrerequisites: () => [], scenarios: [{ id: "edit" }] };
    const report = await runLive(["windsurf"], options({}, { windsurf: witnessed }));
    expect(report.harnesses[0].scenarios[0].reason).toBe("missing prerequisite: Windsurf requires a witnessed procedure");
  });

  test("the OpenCode driver reports its unimplemented scenarios as blocked, never passed", () => {
    const pending = DRIVERS.opencode.scenarios.filter((s) => s.notImplemented).map((s) => s.id);
    expect(pending).toEqual(["idle-feedback-limit", "reload-cleanup"]);
  });
});

describe("live scenario execution", () => {
  const runtime = fakeRuntime("opencode-pinned", "opencode v2.0.30");

  test("scenarios run in a fresh fixture with the real Sentinel behind a recording wrapper", async () => {
    const driver: LiveDriver = {
      harness: "opencode",
      missingPrerequisites: () => [],
      scenarios: [
        {
          id: "wrapper-reaches-real-sentinel",
          async execute(context) {
            const result = await context.run(["sentinel", "--version"], 60_000);
            const recorded = context.invocations();
            return [
              { name: "real sentinel answered", passed: result.exitCode === 0 && result.stdout.startsWith("sentinel ") },
              { name: "invocation recorded", passed: recorded.length === 1 && recorded[0].argv[0] === "--version" },
              { name: "credentials outside the allowlist are not passed", passed: context.env.UNRELATED_SECRET === undefined },
            ];
          },
        },
      ],
    };
    const report = await runLive(
      ["opencode"],
      options({ SENTINEL_COMPAT_OPENCODE_BIN: runtime, UNRELATED_SECRET: "x" }, { opencode: driver }),
    );
    expect(report.harnesses[0].scenarios[0]).toMatchObject({ outcome: "pass", runtimeVersion: "2.0.30", sourceRevision: "rev-under-test" });
    expect(report.exitCode).toBe(0);
  });

  test("a failed assertion or a thrown scenario fails, and a partial matrix is non-success", async () => {
    const driver: LiveDriver = {
      harness: "opencode",
      missingPrerequisites: () => [],
      scenarios: [
        { id: "fails", execute: async () => [{ name: "feedback delivered", passed: false }] },
        { id: "throws", execute: async () => { throw new Error("runtime crashed"); } },
        { id: "absent", unsupported: "documented absent capability" },
      ],
    };
    const report = await runLive(["opencode", "gemini"], options({ SENTINEL_COMPAT_OPENCODE_BIN: runtime }, { opencode: driver }));
    expect(report.harnesses.map((h) => [h.harness, h.outcome])).toEqual([
      ["opencode", "fail"],
      ["gemini", "blocked"],
    ]);
    expect(report.harnesses[0].scenarios.map((s) => s.outcome)).toEqual(["fail", "fail", "unsupported"]);
    expect(report.exitCode).toBe(1);
  });

  test("the Claude Code driver launches the runtime on the generated settings, and a runtime that runs no hooks fails", async () => {
    // A stand-in `claude` that records how it was launched but never dispatches hooks.
    const launches = join(base, "claude-launches");
    const claude = join(base, "claude-no-hooks");
    writeFileSync(
      claude,
      `#!/bin/sh
if [ "$1" = "--version" ]; then echo "2.1.292 (Claude Code)"; exit 0; fi
if grep -q '/.claude/hooks/sentinel' .claude/settings.json 2>/dev/null; then s=settings; else s=none; fi
printf '%s %s %s\\n' "$1" "$s" "\${CLAUDE_CONFIG_DIR:+isolated}" >> '${launches}'
`,
    );
    chmodSync(claude, 0o755);
    const report = await runLive(["claude-code"], options({ SENTINEL_COMPAT_CLAUDE_CODE_BIN: claude, ANTHROPIC_API_KEY: "test-key" }));
    expect(readFileSync(launches, "utf8").trim().split("\n")).toEqual(Array(4).fill("-p settings isolated"));
    expect(report.harnesses[0].scenarios.map((s) => s.outcome)).toEqual(["fail", "fail", "fail", "fail"]);
    expect(report.exitCode).toBe(1);
  }, 120_000);
});

test("the live command prints a report and exits non-zero for blocked harnesses", async () => {
  const child = Bun.spawn(["bun", "run", "src/cli.ts", "live", "--tools", join(base, "cli-tools"), "codex"], {
    cwd: join(REPO_ROOT, "compatibility"),
    env: { PATH: process.env.PATH ?? "", HOME: home },
    stdout: "pipe",
  });
  const report = JSON.parse(await new Response(child.stdout).text());
  expect(await child.exited).toBe(1);
  expect(report.harnesses[0]).toMatchObject({ harness: "codex", outcome: "blocked" });
});

test("every manifest harness is accounted for by the live command", () => {
  expect(loadManifest().harnesses.length).toBe(10);
});
