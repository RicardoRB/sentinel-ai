import { afterAll, describe, expect, test } from "bun:test";
import { existsSync, readFileSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { createFixture, FixtureError, wardLauncher } from "../src/fixture";
import { cleanupOwnedProcesses, ownedProcessCount, run, SYSTEM_PATH } from "../src/process";

afterAll(cleanupOwnedProcesses);

const launcher = wardLauncher(process.env);

// Global harness configuration that a fixture must never create or modify.
const GLOBAL_PATHS = [
  ".codex",
  ".cursor",
  ".gemini",
  ".copilot",
  ".claude/settings.json",
  ".factory",
  ".config/amp",
  ".config/opencode",
  ".codeium",
  "Documents/Cline",
].map((path) => join(homedir(), path));

// A killed orphan stays visible as a zombie until init reaps it, so wait for it to disappear.
const isGone = async (pid: number, timeoutMs = 2_000): Promise<boolean> => {
  const deadline = performance.now() + timeoutMs;
  while (performance.now() < deadline) {
    try {
      process.kill(pid, 0);
    } catch {
      return true;
    }
    await Bun.sleep(20);
  }
  return false;
};

const snapshot = () =>
  GLOBAL_PATHS.map((path) => (existsSync(path) ? `${path}:${statSync(path).mtimeMs}` : `${path}:absent`));

describe("disposable fixtures", () => {
  test("a built Ward launcher is required, not skipped", () => {
    // Build with ./mvnw package (or the native profile) before running compatibility tests.
    expect(launcher).toBeDefined();
  });

  test("integrates with real Ward output inside an isolated home", async () => {
    const before = snapshot();
    const fixture = await createFixture({ harness: "codex", guard: true, ward: launcher! });
    try {
      const base = dirname(fixture.root);
      expect(readFileSync(join(fixture.root, ".codex/hooks.json"), "utf8")).toContain("ward-owned: codex-native-hooks v1");
      expect(readFileSync(join(fixture.root, ".codex/hooks/ward.sh"), "utf8")).toContain("--guard-approved-specs");
      for (const key of ["HOME", "XDG_CONFIG_HOME", "XDG_CACHE_HOME", "XDG_DATA_HOME", "CODEX_HOME", "CLAUDE_CONFIG_DIR", "COPILOT_HOME", "TMPDIR"]) {
        expect(fixture.env[key].startsWith(base)).toBe(true);
      }
      expect(fixture.env.PATH).toBe(SYSTEM_PATH);
    } finally {
      fixture.cleanup();
    }
    expect(existsSync(fixture.root)).toBe(false);
    expect(snapshot()).toEqual(before);
  });

  test("a failed integration cleans up and reports no captured output", async () => {
    let error: unknown;
    try {
      await createFixture({ harness: "not-a-harness", guard: false, ward: launcher! });
    } catch (caught) {
      error = caught;
    }
    expect(error).toBeInstanceOf(FixtureError);
    expect((error as Error).message).toMatch(/^ward integrate not-a-harness did not succeed \(exit \d+\)$/);
  });
});

describe("owned processes", () => {
  test("a deadline terminates the whole process group", async () => {
    const result = await run(["/bin/sh", "-c", "sleep 30 & echo $!; sleep 30"], {
      cwd: "/",
      env: { PATH: SYSTEM_PATH },
      timeoutMs: 300,
    });
    expect(result.timedOut).toBe(true);
    const background = Number(result.stdout.trim());
    expect(background).toBeGreaterThan(0);
    expect(await isGone(background)).toBe(true);
    expect(ownedProcessCount()).toBe(0);
  });

  test("descendants left behind by a finished command are cleaned up", async () => {
    const result = await run(["/bin/sh", "-c", "(sleep 30 &) ; echo started"], {
      cwd: "/",
      env: { PATH: SYSTEM_PATH },
      timeoutMs: 5_000,
    });
    expect(result.exitCode).toBe(0);
    expect(result.timedOut).toBe(false);
  });

  test("the environment is exactly what the caller passes", async () => {
    const result = await run(["/usr/bin/env"], { cwd: "/", env: { PATH: SYSTEM_PATH, ONLY: "1" }, timeoutMs: 5_000 });
    expect(result.stdout.trim().split("\n").sort()).toEqual(["ONLY=1", `PATH=${SYSTEM_PATH}`]);
  });

  test("a spawn failure is reported without throwing", async () => {
    const result = await run(["/nonexistent/binary"], { cwd: "/", env: {}, timeoutMs: 1_000 });
    expect(result.exitCode).toBeNull();
    expect(result.spawnError).toBeDefined();
  });
});
