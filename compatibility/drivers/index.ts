import type { LiveDriver } from "../src/live";
import { ampDriver } from "./amp";
import { claudeCodeDriver } from "./claude-code";
import { codexDriver } from "./codex";
import { copilotDriver } from "./copilot";
import { factoryDroidDriver } from "./factory-droid";
import { geminiDriver } from "./gemini";
import { opencodeDriver } from "./opencode";

/** Live drivers by harness ID; a harness without one is reported blocked, never skipped. */
export const DRIVERS: Record<string, LiveDriver> = {
  amp: ampDriver,
  "claude-code": claudeCodeDriver,
  codex: codexDriver,
  copilot: copilotDriver,
  "factory-droid": factoryDroidDriver,
  gemini: geminiDriver,
  opencode: opencodeDriver,
};
