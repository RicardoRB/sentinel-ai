import type { LiveContext, LiveDriver } from "../src/live";
import { hookScenarios, prepareSmokeFixture } from "./hook-scenarios";

// Live Factory Droid driver. `droid exec` runs one headless turn in the fixture and `--auto high`
// allows its edits and shell commands; Droid itself loads .factory/hooks.json and dispatches the
// hooks. HOME is isolated by the fixture, so no user hooks or settings are loaded. Droid 0.243.0
// asks for folder trust only in the interactive TUI, so exec mode needs no trust state.

const TURN_TIMEOUT_MS = 240_000;

const turn = (context: LiveContext, prompt: string) =>
  context.run([context.runtime.executable, "exec", "--auto", "high", prompt], TURN_TIMEOUT_MS);

export const factoryDroidDriver: LiveDriver = {
  harness: "factory-droid",
  missingPrerequisites: (env) => (env.FACTORY_API_KEY ? [] : ["FACTORY_API_KEY"]),
  prepare: prepareSmokeFixture,
  scenarios: hookScenarios(turn),
};
