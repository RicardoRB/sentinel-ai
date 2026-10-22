import type { LiveContext, LiveDriver } from "../src/live";
import { hookScenarios, prepareSmokeFixture } from "./hook-scenarios";

// Live Cline driver. `cline --yolo` runs one headless turn in the fixture and skips approval prompts;
// Cline loads .cline/hooks/PreToolUse and PostToolUse itself. HOME is isolated by the fixture, so the
// provider is passed on the command line (-P, -m, -k) instead of from a saved sign-in. Cline has no
// turn-end delivery, so the turn-end scenario is unsupported.

const TURN_TIMEOUT_MS = 240_000;

/** Cline provider IDs whose API key comes from the credential allowlist. */
const PROVIDER_KEYS: Record<string, string> = {
  anthropic: "ANTHROPIC_API_KEY",
  openai: "OPENAI_API_KEY",
  openrouter: "OPENROUTER_API_KEY",
  gemini: "GEMINI_API_KEY",
};

const turn = (context: LiveContext, prompt: string) => {
  const provider = process.env.WARD_COMPAT_CLINE_PROVIDER as string;
  const model = process.env.WARD_COMPAT_CLINE_MODEL as string;
  return context.run(
    [context.runtime.executable, "--yolo", "-P", provider, "-m", model, "-k", context.env[PROVIDER_KEYS[provider]], prompt],
    TURN_TIMEOUT_MS,
  );
};

export const clineDriver: LiveDriver = {
  harness: "cline",
  missingPrerequisites: (env) => {
    const provider = env.WARD_COMPAT_CLINE_PROVIDER ?? "";
    const missing: string[] = [];
    if (!PROVIDER_KEYS[provider]) missing.push(`WARD_COMPAT_CLINE_PROVIDER (one of ${Object.keys(PROVIDER_KEYS).join(", ")})`);
    if (!env.WARD_COMPAT_CLINE_MODEL) missing.push("WARD_COMPAT_CLINE_MODEL");
    if (PROVIDER_KEYS[provider] && !env[PROVIDER_KEYS[provider]]) missing.push(PROVIDER_KEYS[provider]);
    return missing;
  },
  prepare: prepareSmokeFixture,
  scenarios: hookScenarios(turn).map((scenario) =>
    scenario.id === "turn-end-feedback-limit"
      ? { id: scenario.id, unsupported: "TaskComplete hooks run detached; Cline ignores their output." }
      : scenario,
  ),
};
