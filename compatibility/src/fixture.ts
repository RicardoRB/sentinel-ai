import { accessSync, constants, cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { REPO_ROOT } from "./manifest";
import { run, SYSTEM_PATH, type RunResult } from "./process";

/** The trusted offline Maven fixture whose stub mvnw is the only command gates execute. */
export const TRUSTED_FIXTURE = join(REPO_ROOT, "src/test/resources/fixtures/maven-project");

export interface WardLauncher {
  argv: string[];
}

/**
 * Resolves the built Ward launcher: an explicit WARD_COMPAT_WARD executable, the
 * native image, or the packaged JAR. Fixtures always use real Ward output, never templates.
 */
export function wardLauncher(env: Record<string, string | undefined>): WardLauncher | undefined {
  const explicit = env.WARD_COMPAT_WARD;
  if (explicit) return executable(explicit) ? { argv: [explicit] } : undefined;
  // Use the most recently built launcher so a stale native image never stands in for a newer JAR.
  const candidates: { argv: string[]; built: number }[] = [];
  const native = join(REPO_ROOT, "target", "ward");
  if (executable(native)) candidates.push({ argv: [native], built: statSync(native).mtimeMs });
  const jar = join(REPO_ROOT, "target", "ward.jar");
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
  ward: WardLauncher;
  cleanup(): void;
}

export interface FixtureOptions {
  harness: string;
  guard: boolean;
  ward: WardLauncher;
  timeoutMs?: number;
  /** Gates passed to `ward init`; defaults to `format`. */
  gates?: string[];
}

export class FixtureError extends Error {}

/**
 * Creates a disposable project from the trusted fixture and integrates `harness` with the real
 * Ward CLI. Every configuration root a harness or Ward could write is redirected into
 * the fixture, so user and global files are never touched.
 */
export async function createFixture(options: FixtureOptions): Promise<Fixture> {
  const base = mkdtempSync(join(tmpdir(), `ward-compat-${options.harness}-`));
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
    ward: options.ward,
    cleanup: () => rmSync(base, { recursive: true, force: true }),
  };
  try {
    const ward = (args: string[]) =>
      run([...options.ward.argv, ...args, "-C", root], { cwd: root, env, timeoutMs: options.timeoutMs ?? 60_000 });
    await expectSuccess("ward init", ward(["init", "--integration", "none", ...(options.gates ?? ["format"]).flatMap((gate) => ["--gate", gate])]));
    const integrate = ["integrate", options.harness];
    if (options.guard) integrate.push("--guard-approved-specs");
    await expectSuccess(`ward integrate ${options.harness}`, ward(integrate));
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
