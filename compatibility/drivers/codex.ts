import type { LiveDriver } from "../src/live";

// Live Codex driver. Codex 0.163.0 runs non-managed hooks only after they are trusted: trust is
// stored per hook in the user config as `trusted_hash`, keyed by the hook source path and a
// sha256 of the normalized handler (codex-rs/hooks/src/engine/discovery.rs, config/src/fingerprint.rs).
// A disposable fixture has a new path on every run, so a live run must write that trust state into
// the isolated CODEX_HOME. The key and hash format can only be confirmed against an installed Codex;
// until then every scenario is reported blocked rather than run with untrusted (skipped) hooks.
const TRUST = "trust bootstrap for generated hooks in the isolated CODEX_HOME (needs an installed Codex to confirm the hash format)";

export const codexDriver: LiveDriver = {
  harness: "codex",
  missingPrerequisites: (env) => (env.OPENAI_API_KEY ? [] : ["OPENAI_API_KEY"]),
  scenarios: [
    { id: "edit-check-feedback", notImplemented: TRUST },
    { id: "guard-refuses-protected-edit", notImplemented: TRUST },
    { id: "guard-allows-active-change-edit", notImplemented: TRUST },
    { id: "stop-feedback-limit", notImplemented: TRUST },
  ],
};
