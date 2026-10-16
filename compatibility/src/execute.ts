import { chmodSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import type { Assertion } from "./evidence";
import type { Fixture } from "./fixture";
import { run, SYSTEM_PATH, type RunResult } from "./process";

/** Documented registration shapes; each harness declares which one its runtime reads. */
export type RegistrationShape =
  /** event -> [{ matcher?, hooks: [{ type: "command", command }] }] (Claude Code, Codex, Gemini, Factory). */
  | "matcher-groups"
  /** { version: 1, hooks: event -> [{ command }] } (Cursor). */
  | "versioned-commands"
  /** { hooks: event -> [{ command }] } (Windsurf). */
  | "commands"
  /** { version: 1, hooks: event -> [{ type: "command", bash }] } (Copilot CLI). */
  | "copilot";

export interface Registration {
  event: string;
  command: string;
  matcher?: string;
}

export class InvalidRegistration extends Error {}

/**
 * Parses a generated configuration strictly against its documented shape. Entries a real runtime
 * would ignore or reject raise {@link InvalidRegistration} instead of being silently accepted.
 */
export function registrations(json: string, shape: RegistrationShape, root = "hooks"): Registration[] {
  let config: any;
  try {
    config = JSON.parse(json);
  } catch {
    throw new InvalidRegistration("configuration is not valid JSON");
  }
  if ((shape === "versioned-commands" || shape === "copilot") && config?.version !== 1) {
    throw new InvalidRegistration("configuration must declare version 1");
  }
  const events = root ? config?.[root] : config;
  if (!events || typeof events !== "object" || Array.isArray(events)) {
    throw new InvalidRegistration(`configuration has no ${root || "event"} object`);
  }
  const found: Registration[] = [];
  for (const [event, entries] of Object.entries(events)) {
    if (!Array.isArray(entries)) throw new InvalidRegistration(`${event} must be a list`);
    entries.forEach((entry: any, index: number) => {
      const where = `${event}[${index}]`;
      if (shape === "matcher-groups") {
        if (!Array.isArray(entry?.hooks) || entry.hooks.length === 0) {
          throw new InvalidRegistration(`${where} must be a matcher group with a hooks list`);
        }
        entry.hooks.forEach((handler: any, inner: number) => {
          if (handler?.type !== "command" || typeof handler.command !== "string" || !handler.command) {
            throw new InvalidRegistration(`${where}.hooks[${inner}] must be a command handler`);
          }
          found.push({ event, command: handler.command, ...(entry.matcher ? { matcher: entry.matcher } : {}) });
        });
      } else if (shape === "copilot") {
        if (entry?.type !== "command" || typeof entry.bash !== "string" || !entry.bash) {
          throw new InvalidRegistration(`${where} must be a command entry with a bash command`);
        }
        found.push({ event, command: entry.bash, ...(entry.matcher ? { matcher: entry.matcher } : {}) });
      } else {
        if (typeof entry?.command !== "string" || !entry.command || entry.hooks !== undefined) {
          throw new InvalidRegistration(`${where} must be a flat command entry`);
        }
        found.push({ event, command: entry.command, ...(entry.matcher ? { matcher: entry.matcher } : {}) });
      }
    });
  }
  return found;
}

export interface Invocation {
  argv: string[];
  cwd: string;
  stdin: string;
}

export interface StubBehavior {
  exitCode?: number;
  stdout?: string;
  stderr?: string;
}

const STUB = `#!/bin/sh
# Recording stand-in for the Sentinel CLI used by generated-artifact execution tests.
# Configuration comes from the environment or, for hosts that spawn with their startup
# environment, from a state directory next to the stub.
state="$(dirname "$0")/.state"
log="\${SENTINEL_STUB_LOG:-$state/log}"
responses="\${SENTINEL_STUB_RESPONSES:-$state/responses}"
mkdir -p "$log" || exit 97
# mkdir is atomic, so concurrent invocations claim distinct, ordered sequence numbers.
i=0
while ! mkdir "$log/$i" 2>/dev/null; do
  i=$((i + 1))
  [ "$i" -gt 100000 ] && exit 97
done
dir="$log/$i"
for arg in "$@"; do printf '%s\\0' "$arg"; done > "$dir/argv"
pwd -P > "$dir/cwd"
cat > "$dir/stdin"
# Written last, so readers never observe a partially recorded invocation.
: > "$dir/complete"
# Optional per-subcommand responses: $SENTINEL_STUB_RESPONSES/<command>[-<subcommand>]/{exit,stdout,stderr,delay}; hook calls are keyed hook-guard or hook-check.
key="$1"; [ "$1" = spec ] && key="spec-$2"
if [ "$1" = hook ]; then
  case " $* " in *" --guard-approved-specs "*) key="hook-guard" ;; *) key="hook-check" ;; esac
fi
response="$responses/$key"
if [ -d "$response" ]; then
  [ -f "$response/delay" ] && sleep "$(cat "$response/delay")"
  [ -f "$response/stdout" ] && cat "$response/stdout"
  [ -f "$response/stderr" ] && cat "$response/stderr" >&2
  exit "$(cat "$response/exit" 2>/dev/null || echo 0)"
fi
[ -n "$SENTINEL_STUB_STDOUT" ] && printf '%s' "$SENTINEL_STUB_STDOUT"
[ -n "$SENTINEL_STUB_STDERR" ] && printf '%s' "$SENTINEL_STUB_STDERR" >&2
exit "\${SENTINEL_STUB_EXIT:-0}"
`;

export interface ExecutionHarness {
  stubBin: string;
  log: string;
  /**
   * Runs one registered command the way a harness launches hook commands: through `sh -c` in
   * `cwd`, with the event payload on stdin and the recording stub first on PATH.
   */
  execute(command: string, options: { cwd: string; stdin: string; env?: Record<string, string>; stub?: StubBehavior }): Promise<{
    result: RunResult;
    invocations: Invocation[];
  }>;
}

/** Writes the recording stub into `dir` and returns its path. */
export function writeStub(dir: string): string {
  mkdirSync(dir, { recursive: true });
  const path = join(dir, "sentinel");
  writeFileSync(path, STUB);
  chmodSync(path, 0o755);
  return path;
}

/** Reads every invocation the stub recorded under `dir`, in order. */
export function recordedInvocations(dir: string): Invocation[] {
  return readInvocations(dir);
}

export function executionHarness(fixture: Fixture): ExecutionHarness {
  const base = dirname(fixture.root);
  const stubBin = join(base, "stub-bin");
  const log = join(base, "stub-log");
  mkdirSync(stubBin, { recursive: true });
  writeFileSync(join(stubBin, "sentinel"), STUB);
  chmodSync(join(stubBin, "sentinel"), 0o755);
  let sequence = 0;
  return {
    stubBin,
    log,
    async execute(command, options) {
      const runLog = join(log, String(sequence++));
      mkdirSync(runLog, { recursive: true });
      const stub = options.stub ?? {};
      const result = await run(["/bin/sh", "-c", command], {
        cwd: options.cwd,
        stdin: options.stdin,
        timeoutMs: 30_000,
        env: {
          ...fixture.env,
          ...options.env,
          PATH: `${stubBin}:${SYSTEM_PATH}`,
          SENTINEL_STUB_LOG: runLog,
          SENTINEL_STUB_EXIT: String(stub.exitCode ?? 0),
          SENTINEL_STUB_STDOUT: stub.stdout ?? "",
          SENTINEL_STUB_STDERR: stub.stderr ?? "",
        },
      });
      return { result, invocations: readInvocations(runLog) };
    },
  };
}

function readInvocations(dir: string): Invocation[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((entry) => existsSync(join(dir, entry, "complete")))
    .sort((a, b) => Number(a) - Number(b))
    .map((entry) => {
      const at = join(dir, entry);
      const argv = readFileSync(join(at, "argv"), "utf8").split("\0").slice(0, -1);
      return { argv, cwd: readFileSync(join(at, "cwd"), "utf8").trim(), stdin: readFileSync(join(at, "stdin"), "utf8") };
    });
}

/** Records named assertions so evidence reflects exactly what was executed. */
export class Assertions {
  readonly list: Assertion[] = [];

  check(name: string, passed: boolean, detail?: string): boolean {
    this.list.push({ name, passed, ...(detail === undefined ? {} : { detail }) });
    return passed;
  }

  /** The generated artifact reached Sentinel exactly once with these arguments. */
  invokedSentinel(invocations: Invocation[], argv: string[]): boolean {
    const passed = invocations.length === 1 && JSON.stringify(invocations[0].argv) === JSON.stringify(argv);
    return this.check(
      `invokes sentinel ${argv.join(" ")}`,
      passed,
      `observed ${invocations.length} invocation(s)${invocations.length ? `: ${invocations.map((i) => i.argv.join(" ")).join(" | ")}` : ""}`,
    );
  }

  get passed(): boolean {
    return this.list.length > 0 && this.list.every((assertion) => assertion.passed);
  }
}

/**
 * Negative control: rewrites a generated wrapper so it no longer reaches Sentinel. Assertions that
 * still pass against a disconnected artifact are not evidence of wiring.
 */
export function disconnectWrapper(path: string): void {
  const original = readFileSync(path, "utf8");
  writeFileSync(path, original.replace(/exec sentinel [^;\n]*/g, "exit 0"));
}
