import { accessSync, constants, existsSync, mkdirSync } from "node:fs";
import { dirname, isAbsolute, join } from "node:path";
import type { Harness } from "./manifest";
import { run, SYSTEM_PATH } from "./process";

export type PreflightStatus = "ready" | "blocked";

export interface PreflightResult {
  harness: string;
  status: PreflightStatus;
  executable?: string;
  expectedVersion: string;
  foundVersion?: string;
  prerequisite?: string;
}

export interface PreflightOptions {
  /** Isolated directory for opt-in pinned installs; never a global location. */
  toolsDir: string;
  /** Caller environment, used only to read the explicit executable override. */
  env: Record<string, string | undefined>;
  /** Isolated HOME for running the runtime's version command. */
  home: string;
  timeoutMs?: number;
}

/**
 * Locates a pinned runtime from its explicit executable override or the isolated tools
 * directory, then verifies its exact version. PATH lookup is never used, so an unrelated global
 * installation cannot satisfy preflight.
 */
export async function preflight(harness: Harness, options: PreflightOptions): Promise<PreflightResult> {
  const expectedVersion = harness.runtime.version;
  const blocked = (prerequisite: string, extra: Partial<PreflightResult> = {}): PreflightResult => ({
    harness: harness.id,
    status: "blocked",
    expectedVersion,
    prerequisite,
    ...extra,
  });
  const executable = locate(harness, options);
  if (!executable) {
    return blocked(
      `${harness.runtime.executable} ${expectedVersion} (set ${harness.runtime.executableEnv} to an absolute executable path${
        harness.runtime.install.length ? " or run preflight with --install" : ""
      })`,
    );
  }
  const result = await run([executable, ...harness.runtime.versionCommand], {
    cwd: options.home,
    env: { PATH: `${dirname(executable)}:${SYSTEM_PATH}`, HOME: options.home },
    timeoutMs: options.timeoutMs ?? 30_000,
  });
  if (result.timedOut || result.exitCode !== 0) {
    return blocked(`${harness.runtime.executable} version command did not succeed`, { executable });
  }
  const foundVersion = parseVersion(harness, `${result.stdout}\n${result.stderr}`);
  if (foundVersion !== expectedVersion) {
    return blocked(`version mismatch: expected ${expectedVersion}, found ${foundVersion ?? "unrecognized output"}`, {
      executable,
      ...(foundVersion ? { foundVersion } : {}),
    });
  }
  return { harness: harness.id, status: "ready", executable, expectedVersion, foundVersion };
}

export function parseVersion(harness: Harness, output: string): string | undefined {
  const pattern = new RegExp(harness.runtime.versionPattern);
  for (const line of output.split("\n")) {
    const match = pattern.exec(line.trim());
    if (match?.[1]) return match[1];
  }
  return undefined;
}

function locate(harness: Harness, options: PreflightOptions): string | undefined {
  const override = options.env[harness.runtime.executableEnv];
  if (override) return isAbsolute(override) && executableFile(override) ? override : undefined;
  const installed = join(options.toolsDir, "node_modules", ".bin", harness.runtime.executable);
  return executableFile(installed) ? installed : undefined;
}

function executableFile(path: string): boolean {
  try {
    accessSync(path, constants.X_OK);
    return true;
  } catch {
    return false;
  }
}

export interface InstallResult {
  harness: string;
  installed: boolean;
  prerequisite?: string;
}

/**
 * Performs the manifest's pinned install into the isolated tools directory. Harnesses without an
 * isolated install (editors, vendor installers) report the explicit executable they require.
 */
export async function install(
  harness: Harness,
  options: { toolsDir: string; npm: string; home: string; timeoutMs?: number },
): Promise<InstallResult> {
  if (harness.runtime.install.length === 0) {
    return {
      harness: harness.id,
      installed: false,
      prerequisite: `${harness.label} has no isolated installer; set ${harness.runtime.executableEnv}`,
    };
  }
  if (!existsSync(options.npm)) {
    return { harness: harness.id, installed: false, prerequisite: `npm executable ${options.npm}` };
  }
  mkdirSync(options.toolsDir, { recursive: true });
  const argv = harness.runtime.install.map((part) => (part === "${TOOLS}" ? options.toolsDir : part));
  const result = await run([options.npm, ...argv.slice(1)], {
    cwd: options.toolsDir,
    // npm's own cache and config stay inside the isolated home.
    env: {
      PATH: `${dirname(options.npm)}:${SYSTEM_PATH}`,
      HOME: options.home,
      npm_config_cache: join(options.home, ".npm"),
      npm_config_userconfig: join(options.home, ".npmrc"),
    },
    timeoutMs: options.timeoutMs ?? 300_000,
  });
  if (result.timedOut || result.exitCode !== 0) {
    return { harness: harness.id, installed: false, prerequisite: `pinned install of ${argv.at(-1)} failed` };
  }
  return { harness: harness.id, installed: true };
}
