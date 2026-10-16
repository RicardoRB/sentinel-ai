import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type { Fixture } from "../src/fixture";
import type { LiveContext, LiveDriver } from "../src/live";
import { hookScenarios, prepareSmokeFixture } from "./hook-scenarios";

// Live Gemini CLI driver. `gemini -p` runs one headless turn in the fixture and `--approval-mode yolo`
// approves its tool calls; Gemini itself loads .gemini/settings.json and dispatches the hooks. HOME is
// isolated by the fixture, so no user settings or extensions are loaded. Gemini registers project
// hooks only in a trusted folder (hookRegistry.js), so the fixture is trusted in the isolated
// ~/.gemini/trustedFolders.json.

const TURN_TIMEOUT_MS = 240_000;

function prepare(fixture: Fixture): void {
  prepareSmokeFixture(fixture);
  const home = join(fixture.env.HOME, ".gemini");
  mkdirSync(home, { recursive: true });
  writeFileSync(join(home, "trustedFolders.json"), JSON.stringify({ [fixture.root]: "TRUST_FOLDER" }));
}

const turn = (context: LiveContext, prompt: string) =>
  context.run([context.runtime.executable, "-p", prompt, "--approval-mode", "yolo"], TURN_TIMEOUT_MS);

export const geminiDriver: LiveDriver = {
  harness: "gemini",
  missingPrerequisites: (env) => (env.GEMINI_API_KEY ? [] : ["GEMINI_API_KEY"]),
  prepare,
  scenarios: hookScenarios(turn),
};
