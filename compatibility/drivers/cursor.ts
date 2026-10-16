import type { LiveContext, LiveDriver } from "../src/live";
import { hookScenarios, prepareSmokeFixture } from "./hook-scenarios";

// Live Cursor driver. `cursor-agent -p` runs one headless turn in the fixture and `--force` allows its
// commands; cursor-agent loads .cursor/hooks.json itself and runs the hooks from the project root.
// HOME is isolated by the fixture, so no user hooks or settings are loaded. cursor-agent also reads
// Claude Code settings, which the fixture does not contain.

const TURN_TIMEOUT_MS = 240_000;

const turn = (context: LiveContext, prompt: string) =>
  context.run([context.runtime.executable, "-p", prompt, "--force", "--output-format", "text"], TURN_TIMEOUT_MS);

export const cursorDriver: LiveDriver = {
  harness: "cursor",
  missingPrerequisites: (env) => (env.CURSOR_API_KEY ? [] : ["CURSOR_API_KEY"]),
  prepare: prepareSmokeFixture,
  scenarios: hookScenarios(turn),
};
