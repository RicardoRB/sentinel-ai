import type { LiveDriver } from "../src/live";
import { ampDriver } from "./amp";
import { claudeCodeDriver } from "./claude-code";
import { codexDriver } from "./codex";
import { copilotDriver } from "./copilot";
import { opencodeDriver } from "./opencode";

/** Live drivers by harness ID; a harness without one is reported blocked, never skipped. */
export const DRIVERS: Record<string, LiveDriver> = {
  amp: ampDriver,
  "claude-code": claudeCodeDriver,
  codex: codexDriver,
  copilot: copilotDriver,
  opencode: opencodeDriver,
};
