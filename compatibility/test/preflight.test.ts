import { afterAll, describe, expect, test } from "bun:test";
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { findHarness, loadManifest, REPO_ROOT } from "../src/manifest";
import { install, parseVersion, preflight } from "../src/preflight";

const manifest = loadManifest();
const codex = findHarness(manifest, "codex");
const windsurf = findHarness(manifest, "windsurf");
const base = mkdtempSync(join(tmpdir(), "sentinel-preflight-"));
const home = join(base, "home");
mkdirSync(home);
afterAll(() => rmSync(base, { recursive: true, force: true }));

function script(name: string, body: string): string {
  const path = join(base, name);
  writeFileSync(path, `#!/bin/sh\n${body}\n`);
  chmodSync(path, 0o755);
  return path;
}

const options = (env: Record<string, string> = {}, toolsDir = join(base, "no-tools")) => ({ toolsDir, env, home });

describe("runtime preflight", () => {
  test("a missing runtime is blocked and names the exact prerequisite", async () => {
    const result = await preflight(codex, options());
    expect(result.status).toBe("blocked");
    expect(result.prerequisite).toBe(
      "codex 0.163.0 (set SENTINEL_COMPAT_CODEX_BIN to an absolute executable path or run preflight with --install)",
    );
  });

  test("an editor runtime without an isolated installer names only the executable override", async () => {
    const result = await preflight(windsurf, options());
    expect(result.prerequisite).toBe("windsurf 1.126.0 (set SENTINEL_COMPAT_WINDSURF_BIN to an absolute executable path)");
  });

  test("PATH and relative overrides never satisfy preflight", async () => {
    expect((await preflight(codex, options({ SENTINEL_COMPAT_CODEX_BIN: "codex", PATH: "/usr/bin" }))).status).toBe("blocked");
  });

  test("a version mismatch is explicit non-success", async () => {
    const old = script("codex-old", 'echo "codex-cli 0.162.0"');
    const result = await preflight(codex, options({ SENTINEL_COMPAT_CODEX_BIN: old }));
    expect(result).toMatchObject({
      status: "blocked",
      foundVersion: "0.162.0",
      prerequisite: "version mismatch: expected 0.163.0, found 0.162.0",
    });
  });

  test("unrecognized version output is a mismatch, not a pass", async () => {
    const odd = script("codex-odd", 'echo "something else"');
    expect((await preflight(codex, options({ SENTINEL_COMPAT_CODEX_BIN: odd }))).prerequisite).toBe(
      "version mismatch: expected 0.163.0, found unrecognized output",
    );
  });

  test("a failing version command is blocked", async () => {
    const broken = script("codex-broken", "exit 3");
    expect((await preflight(codex, options({ SENTINEL_COMPAT_CODEX_BIN: broken }))).prerequisite).toBe(
      "codex version command did not succeed",
    );
  });

  test("the exact pinned version is ready and runs with the isolated HOME", async () => {
    const pinned = script("codex-pinned", `[ "$HOME" = "${home}" ] || exit 9\necho "codex-cli 0.163.0"`);
    expect(await preflight(codex, options({ SENTINEL_COMPAT_CODEX_BIN: pinned }))).toMatchObject({
      status: "ready",
      foundVersion: "0.163.0",
    });
  });

  test("a runtime in the isolated tools directory is located without an override", async () => {
    const tools = join(base, "tools");
    mkdirSync(join(tools, "node_modules", ".bin"), { recursive: true });
    const bin = join(tools, "node_modules", ".bin", "codex");
    writeFileSync(bin, '#!/bin/sh\necho "codex-cli 0.163.0"\n');
    chmodSync(bin, 0o755);
    expect((await preflight(codex, options({}, tools))).executable).toBe(bin);
  });

  test("the manifest version patterns parse the documented version output", () => {
    expect(parseVersion(findHarness(manifest, "opencode"), "opencode v2.0.30")).toBe("2.0.30");
    expect(parseVersion(findHarness(manifest, "claude-code"), "2.1.292 (Claude Code)")).toBe("2.1.292");
  });
});

describe("pinned isolated install", () => {
  test("installs only into the tools directory with the pinned package and isolated npm state", async () => {
    const record = join(base, "npm-args");
    const npm = script("npm-ok", `printf '%s\\n' "$@" > "${record}"\necho "$HOME" >> "${record}"\necho "$npm_config_cache" >> "${record}"`);
    const tools = join(base, "install-tools");
    expect(await install(codex, { toolsDir: tools, npm, home })).toEqual({ harness: "codex", installed: true });
    expect(readFileSync(record, "utf8").trim().split("\n")).toEqual([
      "install",
      "--prefix",
      tools,
      "@openai/codex@0.163.0",
      home,
      join(home, ".npm"),
    ]);
  });

  test("an install failure is explicit non-success", async () => {
    const npm = script("npm-fail", "exit 1");
    expect(await install(codex, { toolsDir: join(base, "t2"), npm, home })).toEqual({
      harness: "codex",
      installed: false,
      prerequisite: "pinned install of @openai/codex@0.163.0 failed",
    });
  });

  test("a missing npm is a named prerequisite", async () => {
    expect((await install(codex, { toolsDir: join(base, "t3"), npm: join(base, "absent-npm"), home })).prerequisite).toBe(
      `npm executable ${join(base, "absent-npm")}`,
    );
  });

  test("harnesses without an isolated installer are never installed", async () => {
    expect(await install(windsurf, { toolsDir: join(base, "t4"), npm: "/usr/bin/true", home })).toEqual({
      harness: "windsurf",
      installed: false,
      prerequisite: "Windsurf has no isolated installer; set SENTINEL_COMPAT_WINDSURF_BIN",
    });
    expect(existsSync(join(base, "t4"))).toBe(false);
  });
});

describe("preflight command", () => {
  test("exits non-zero and reports blocked runtimes as JSON", async () => {
    const child = Bun.spawn(["bun", "run", "src/cli.ts", "preflight", "--tools", join(base, "cli-tools"), "codex"], {
      cwd: join(REPO_ROOT, "compatibility"),
      env: { PATH: process.env.PATH ?? "", HOME: home },
      stdout: "pipe",
      stderr: "pipe",
    });
    const stdout = await new Response(child.stdout).text();
    expect(await child.exited).toBe(1);
    expect(JSON.parse(stdout.trim())).toMatchObject({ harness: "codex", status: "blocked" });
  });

  test("an empty request is a usage error", async () => {
    const child = Bun.spawn(["bun", "run", "src/cli.ts", "preflight"], {
      cwd: join(REPO_ROOT, "compatibility"),
      env: { PATH: process.env.PATH ?? "", HOME: home },
      stderr: "pipe",
    });
    expect(await child.exited).toBe(2);
  });
});
