# Proposal

## Why

Claude Code integration currently treats `.claude/settings.json` as a single Sentinel-owned artifact. Projects with existing user settings or hooks therefore cannot add Sentinel without replacing their configuration or encountering a conflict. Sentinel should coexist with existing Claude Code settings while protecting user-owned entries.

## What Changes

- Merge Sentinel hook registrations into valid existing `.claude/settings.json` while preserving unrelated settings and hooks.
- Remove only Sentinel's hook registrations and preserve all remaining settings when uninstalling.
- Refuse malformed settings without modifying them.
- Keep OpenCode's separate-file integration and exact-path conflict behavior unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-integration`: Claude Code integration must safely merge and remove only Sentinel-owned hook configuration in existing settings.

## Impact

- Affects `ClaudeCodeIntegration` and its tests under `src/main/java/dev/sentinel/infrastructure/agent` and `src/test/java/dev/sentinel/infrastructure/agent`.
- Changes the `.claude/settings.json` integration contract; no CLI interface or dependency change is intended.
- OpenCode integration behavior remains unchanged.
