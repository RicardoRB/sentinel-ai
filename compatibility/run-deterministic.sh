#!/bin/sh
# Runs every deterministic harness compatibility suite against one Sentinel build.
# Usage: compatibility/run-deterministic.sh [path/to/sentinel]
# Without an argument the newest of target/sentinel and target/sentinel.jar is used.
# No harness runtime, network model access or credentials are needed; a failing suite fails this script.
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ "$#" -gt 0 ]; then
  SENTINEL_COMPAT_SENTINEL=$(CDPATH= cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")
  export SENTINEL_COMPAT_SENTINEL
fi

cd "$ROOT"
bun test test/
for suite in opencode-v2 amp claude-code codex copilot gemini factory-droid cursor windsurf cline; do
  echo "== $suite"
  (cd "$suite" && { [ ! -f bun.lock ] || bun install --frozen-lockfile; } && bun run test)
done
