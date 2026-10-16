import { createHash } from "node:crypto";
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { aggregate, record, type Assertion, type MatrixReport, type ScenarioRecord } from "./evidence";
import { recordedInvocations, type Invocation } from "./execute";
import { createFixture, sentinelLauncher, type Fixture, type SentinelLauncher } from "./fixture";
import { findHarness, loadManifest, REPO_ROOT, type Harness, type Manifest } from "./manifest";
import { preflight, type PreflightResult } from "./preflight";
import { run, SYSTEM_PATH } from "./process";

/** Credential variables a live run may pass to a harness; nothing else is inherited. */
export const CREDENTIAL_ALLOWLIST = [
  "ANTHROPIC_API_KEY",
  "OPENAI_API_KEY",
  "OPENROUTER_API_KEY",
  "GEMINI_API_KEY",
  "GH_TOKEN",
  "CURSOR_API_KEY",
  "FACTORY_API_KEY",
  "AMP_API_KEY",
] as const;

export interface LiveContext {
  harness: Harness;
  runtime: PreflightResult & { status: "ready"; executable: string };
  fixture: Fixture;
  /** Fixture environment plus the recording Sentinel wrapper and allowlisted credentials. */
  env: Record<string, string>;
  /** Invocations of the real Sentinel CLI, recorded by the wrapper before it execs Sentinel. */
  invocations(): Invocation[];
  run(argv: string[], timeoutMs: number): ReturnType<typeof run>;
}

export interface LiveScenario {
  id: string;
  /** Set when the scenario has no driver yet; it is reported blocked, never passed. */
  notImplemented?: string;
  /** Set when the manifest documents the capability as absent. */
  unsupported?: string;
  /** False when the scenario needs no model credentials, such as a model-free activation probe. */
  requiresCredentials?: boolean;
  execute?(context: LiveContext): Promise<Assertion[]>;
}

export interface LiveDriver {
  harness: string;
  /** Names of required prerequisites that are missing, given the caller's environment. */
  missingPrerequisites(env: Record<string, string | undefined>): string[];
  /** Fixture preparation beyond `sentinel integrate`, such as protected specs. */
  prepare?(fixture: Fixture): void;
  scenarios: LiveScenario[];
}

/** Writes a `sentinel` wrapper that records each invocation and then execs the real launcher. */
export function writeRecordingWrapper(dir: string, log: string, launcher: SentinelLauncher): void {
  mkdirSync(dir, { recursive: true });
  mkdirSync(log, { recursive: true });
  const quoted = launcher.argv.map((part) => `'${part.replaceAll("'", "'\\''")}'`).join(" ");
  const path = join(dir, "sentinel");
  writeFileSync(
    path,
    `#!/bin/sh
# Records that a native harness event reached Sentinel, then runs the real Sentinel CLI.
i=0
while ! mkdir '${log}'/"$i" 2>/dev/null; do i=$((i + 1)); [ "$i" -gt 100000 ] && break; done
for arg in "$@"; do printf '%s\\0' "$arg"; done > '${log}'/"$i"/argv
pwd -P > '${log}'/"$i"/cwd
: > '${log}'/"$i"/stdin
: > '${log}'/"$i"/complete
exec ${quoted} "$@"
`,
  );
  chmodSync(path, 0o755);
}

export function sha256(path: string): string | undefined {
  return existsSync(path) ? createHash("sha256").update(readFileSync(path)).digest("hex") : undefined;
}

export function sourceRevision(): string {
  const head = Bun.spawnSync(["git", "rev-parse", "HEAD"], { cwd: REPO_ROOT }).stdout.toString().trim();
  const dirty = Bun.spawnSync(["git", "status", "--porcelain"], { cwd: REPO_ROOT }).stdout.toString().trim();
  return dirty ? `${head}+dirty` : head;
}

export interface LiveOptions {
  env: Record<string, string | undefined>;
  toolsDir: string;
  home: string;
  drivers: Record<string, LiveDriver>;
  manifest?: Manifest;
  revision?: string;
}

/**
 * Runs the requested harnesses' live scenarios. Missing runtimes, credentials and unimplemented
 * drivers are blocked with the named prerequisite; the report is non-success unless every
 * requested harness passes.
 */
export async function runLive(requested: string[], options: LiveOptions): Promise<MatrixReport> {
  const manifest = options.manifest ?? loadManifest();
  const revision = options.revision ?? sourceRevision();
  const records: ScenarioRecord[] = [];
  for (const id of requested) records.push(...(await runHarness(findHarness(manifest, id), options, revision)));
  return aggregate(records, requested, "automated-live");
}

async function runHarness(harness: Harness, options: LiveOptions, revision: string): Promise<ScenarioRecord[]> {
  const expected = { runtimeVersion: harness.runtime.version, sourceRevision: revision };
  const blockedAll = (scenarios: string[], prerequisite: string, driverImplemented = true) =>
    scenarios.map((scenario) =>
      record(
        {
          harness: harness.id,
          scenario,
          level: "automated-live",
          claimed: "blocked",
          assertions: [],
          durationMs: 0,
          sourceRevision: revision,
          ...(driverImplemented ? { prerequisite } : { driverImplemented: false }),
        },
        expected,
      ),
    );
  const driver = options.drivers[harness.id];
  if (!driver) return blockedAll(["live-smoke"], "", false);
  if (harness.verification.mode === "witnessed") {
    return blockedAll(driver.scenarios.map((s) => s.id), `${harness.label} requires a witnessed procedure`);
  }
  const runtime = await preflight(harness, { toolsDir: options.toolsDir, env: options.env, home: options.home });
  if (runtime.status !== "ready" || !runtime.executable) {
    return blockedAll(driver.scenarios.map((s) => s.id), runtime.prerequisite ?? "runtime");
  }
  const missing = driver.missingPrerequisites(options.env);
  const launcher = sentinelLauncher(options.env);
  if (!launcher) return blockedAll(driver.scenarios.map((s) => s.id), "built Sentinel launcher (./mvnw package)");

  const records: ScenarioRecord[] = [];
  for (const scenario of driver.scenarios) {
    const base = { harness: harness.id, scenario: scenario.id, level: "automated-live" as const, sourceRevision: revision, runtimeVersion: runtime.foundVersion };
    if (scenario.unsupported) {
      records.push(record({ ...base, claimed: "unsupported", unsupportedProvenance: scenario.unsupported, assertions: [], durationMs: 0 }, expected));
      continue;
    }
    if (scenario.notImplemented || !scenario.execute) {
      records.push(record({ ...base, claimed: "blocked", driverImplemented: false, assertions: [], durationMs: 0 }, expected));
      continue;
    }
    if (missing.length && scenario.requiresCredentials !== false) {
      records.push(record({ ...base, claimed: "blocked", prerequisite: missing.join(", "), assertions: [], durationMs: 0 }, expected));
      continue;
    }
    const started = performance.now();
    const fixture = await createFixture({ harness: harness.id, guard: true, sentinel: launcher });
    try {
      driver.prepare?.(fixture);
      const wrapperDir = join(dirname(fixture.root), "sentinel-bin");
      const log = join(dirname(fixture.root), "sentinel-log");
      writeRecordingWrapper(wrapperDir, log, launcher);
      const credentials = Object.fromEntries(
        CREDENTIAL_ALLOWLIST.filter((name) => options.env[name]).map((name) => [name, options.env[name] as string]),
      );
      const env = { ...fixture.env, ...credentials, PATH: `${wrapperDir}:${dirname(runtime.executable)}:${SYSTEM_PATH}` };
      const context: LiveContext = {
        harness,
        runtime: runtime as LiveContext["runtime"],
        fixture,
        env,
        invocations: () => recordedInvocations(log),
        run: (argv, timeoutMs) => run(argv, { cwd: fixture.root, env, timeoutMs }),
      };
      const assertions = await scenario.execute(context);
      records.push(record({ ...base, claimed: "pass", assertions, durationMs: Math.round(performance.now() - started) }, expected));
    } catch (error) {
      records.push(
        record(
          { ...base, claimed: "fail", assertions: [{ name: "scenario completed", passed: false, detail: error instanceof Error ? error.message : String(error) }], durationMs: Math.round(performance.now() - started) },
          expected,
        ),
      );
    } finally {
      fixture.cleanup();
    }
  }
  return records;
}
