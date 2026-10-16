import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Fixture } from "../src/fixture";
import type { LiveContext, LiveDriver } from "../src/live";
import { hookScenarios, prepareSmokeFixture } from "./hook-scenarios";

// Live Copilot CLI driver. `copilot -p` runs one non-interactive turn in the fixture; Copilot itself
// discovers .github/hooks/sentinel.json and dispatches the hooks. COPILOT_HOME is isolated by the
// fixture, so no user hooks, settings or plugins are loaded. Repository hooks load only from a
// trusted folder, so the fixture is listed in the isolated config's trusted_folders; that key is
// unconfirmed until a live run, and a run that never reaches Sentinel fails its invocation assertion.
// Source: https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-hooks-reference.

const TURN_TIMEOUT_MS = 240_000;

function prepare(fixture: Fixture): void {
  prepareSmokeFixture(fixture);
  const home = fixture.env.COPILOT_HOME;
  mkdirSync(home, { recursive: true });
  writeFileSync(join(home, "config.json"), JSON.stringify({ trusted_folders: [fixture.root] }));
}

const turn = (context: LiveContext, prompt: string) =>
  context.run([context.runtime.executable, "-p", prompt, "--allow-all-tools", "--no-color"], TURN_TIMEOUT_MS);

export const copilotDriver: LiveDriver = {
  harness: "copilot",
  missingPrerequisites: (env) => (env.GH_TOKEN ? [] : ["GH_TOKEN"]),
  prepare,
  scenarios: hookScenarios(turn),
};
