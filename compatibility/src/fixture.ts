import { accessSync, constants, cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { REPO_ROOT } from "./manifest";
import { run, SYSTEM_PATH, type RunResult } from "./process";

/** The trusted offline Maven fixture whose stub mvnw is the only command gates execute. */
export const TRUSTED_FIXTURE = join(REPO_ROOT, "src/test/resources/fixtures/maven-project");

export interface SentinelLauncher {
  argv: string[];
}

/**
 * Resolves the built Sentinel launcher: an explicit SENTINEL_COMPAT_SENTINEL executable, the
 * native image, or the packaged JAR. Fixtures always use real Sentinel output, never templates.
 */
export function sentinelLauncher(env: Record<string, string | undefined>): SentinelLauncher | undefined {
  const explicit = env.SENTINEL_COMPAT_SENTINEL;
  if (explicit) return executable(explicit) ? { argv: [explicit] } : undefined;
  // Use the most recently built launcher so a stale native image never stands in for a newer JAR.
  const candidates: { argv: string[]; built: number }[] = [];
  const native = join(REPO_ROOT, "target", "sentinel");
  if (executable(native)) candidates.push({ argv: [native], built: statSync(native).mtimeMs });
  const jar = join(REPO_ROOT, "target", "sentinel.jar");
  const java = env.JAVA_HOME ? join(env.JAVA_HOME, "bin", "java") : "/usr/bin/java";
  if (executable(java) && existsSync(jar)) candidates.push({ argv: [java, "-jar", jar], built: statSync(jar).mtimeMs });
  candidates.sort((a, b) => b.built - a.built);
  if (candidates.length > 0) return { argv: candidates[0].argv };
  return undefined;
}

export interface Fixture {
  root: string;
  /** Isolated environment: HOME, XDG and harness configuration roots live inside the fixture. */
  env: Record<string, string>;
  sentinel: SentinelLauncher;
  cleanup(): void;
}

export interface FixtureOptions {
  harness: string;
  guard: boolean;
  sentinel: SentinelLauncher;
  timeoutMs?: number;
  /** Gates passed to `sentinel init`; defaults to `format`. */
  gates?: string[];
}

export class FixtureError extends Error {}

/**
 * Creates a disposable project from the trusted fixture and integrates `harness` with the real
 * Sentinel CLI. Every configuration root a harness or Sentinel could write is redirected into
 * the fixture, so user and global files are never touched.
 */
export async function createFixture(options: FixtureOptions): Promise<Fixture> {
  const base = mkdtempSync(join(tmpdir(), `sentinel-compat-${options.harness}-`));
  const root = join(base, "project");
  const home = join(base, "home");
  cpSync(TRUSTED_FIXTURE, root, { recursive: true });
  for (const dir of [home, join(home, ".config"), join(home, ".cache"), join(home, ".local", "share")]) {
    mkdirSync(dir, { recursive: true });
  }
  const env: Record<string, string> = {
    PATH: SYSTEM_PATH,
    HOME: home,
    XDG_CONFIG_HOME: join(home, ".config"),
    XDG_CACHE_HOME: join(home, ".cache"),
    XDG_DATA_HOME: join(home, ".local", "share"),
    CODEX_HOME: join(home, ".codex"),
    CLAUDE_CONFIG_DIR: join(home, ".claude"),
    GEMINI_CLI_HOME: home,
    COPILOT_HOME: join(home, ".copilot"),
    TMPDIR: join(base, "tmp"),
  };
  mkdirSync(env.TMPDIR, { recursive: true });
  const fixture: Fixture = {
    root,
    env,
    sentinel: options.sentinel,
    cleanup: () => rmSync(base, { recursive: true, force: true }),
  };
  try {
    const sentinel = (args: string[]) =>
      run([...options.sentinel.argv, ...args, "-C", root], { cwd: root, env, timeoutMs: options.timeoutMs ?? 60_000 });
    await expectSuccess("sentinel init", sentinel(["init", "--integration", "none", ...(options.gates ?? ["format"]).flatMap((gate) => ["--gate", gate])]));
    const integrate = ["integrate", options.harness];
    if (options.guard) integrate.push("--guard-approved-specs");
    await expectSuccess(`sentinel integrate ${options.harness}`, sentinel(integrate));
    return fixture;
  } catch (error) {
    fixture.cleanup();
    throw error;
  }
}

async function expectSuccess(step: string, pending: Promise<RunResult>): Promise<void> {
  const result = await pending;
  if (result.exitCode !== 0) {
    // Only the step and exit status are reported; captured process output is never surfaced.
    throw new FixtureError(
      `${step} did not succeed (${result.timedOut ? "timed out" : `exit ${result.exitCode ?? result.spawnError}`})`,
    );
  }
}

function executable(path: string): boolean {
  try {
    accessSync(path, constants.X_OK);
    return true;
  } catch {
    return false;
  }
}
