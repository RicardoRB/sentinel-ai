import { spawn } from "node:child_process";

export interface RunOptions {
  cwd: string;
  env: Record<string, string>;
  timeoutMs: number;
  stdin?: string;
}

export interface RunResult {
  exitCode: number | null;
  stdout: string;
  stderr: string;
  timedOut: boolean;
  spawnError?: string;
  durationMs: number;
}

const owned = new Set<number>();

/**
 * Runs an argument vector (never through a shell) in its own process group, so a deadline or
 * cleanup can terminate every descendant it started. The environment is exactly `env`; nothing
 * is inherited from the caller.
 */
export function run(argv: string[], options: RunOptions): Promise<RunResult> {
  const started = performance.now();
  return new Promise((resolve) => {
    let stdout = "";
    let stderr = "";
    let timedOut = false;
    const child = spawn(argv[0], argv.slice(1), {
      cwd: options.cwd,
      env: options.env,
      detached: true,
      stdio: ["pipe", "pipe", "pipe"],
    });
    if (child.pid !== undefined) owned.add(child.pid);
    const timer = setTimeout(() => {
      timedOut = true;
      killGroup(child.pid);
    }, options.timeoutMs);
    child.stdout.on("data", (chunk) => (stdout += chunk));
    child.stderr.on("data", (chunk) => (stderr += chunk));
    child.stdin.on("error", () => {});
    child.stdin.end(options.stdin ?? "");
    const finish = (exitCode: number | null, spawnError?: string) => {
      clearTimeout(timer);
      // Descendants may outlive the leader; the group is ours to clean up either way.
      killGroup(child.pid);
      if (child.pid !== undefined) owned.delete(child.pid);
      resolve({
        exitCode,
        stdout,
        stderr,
        timedOut,
        ...(spawnError ? { spawnError } : {}),
        durationMs: Math.round(performance.now() - started),
      });
    };
    child.on("error", (error) => finish(null, error.message));
    // A leftover descendant holds the output pipes open, so the group is terminated as soon as the
    // leader exits; "close" then fires once every pipe is released.
    child.on("exit", () => killGroup(child.pid));
    child.on("close", (code) => finish(code));
  });
}

function killGroup(pid: number | undefined): void {
  if (pid === undefined) return;
  try {
    process.kill(-pid, "SIGKILL");
  } catch {
    // The group has already exited.
  }
}

/** Terminates every process group that is still owned, for use in test teardown. */
export function cleanupOwnedProcesses(): void {
  for (const pid of owned) killGroup(pid);
  owned.clear();
}

export function ownedProcessCount(): number {
  return owned.size;
}

/** A minimal PATH that never exposes user-installed harnesses or tools by accident. */
export const SYSTEM_PATH = "/usr/bin:/bin:/usr/sbin:/sbin";
