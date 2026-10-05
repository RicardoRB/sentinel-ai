## Why

`sentinel integrate` currently creates only an OpenCode discovery command and does not place a quality check at the point where an agent edits or writes project files. This leaves Claude Code and OpenCode workflows able to make changes without an immediate Sentinel verification boundary, so the integration should provision native, ownership-safe edit/write guards for both supported agent environments.

## What Changes

- Extend `sentinel integrate` to support Claude Code project hooks, creating the required hook configuration and executable wrapper when they are absent.
- Extend the OpenCode integration to provision a plugin or equivalent event handler that runs `sentinel check --format json` for edit/write operations.
- Ensure every generated guard invokes Sentinel only for applicable edit/write events, reports failures to the agent, and preserves the agent's ability to stop or surface the failure.
- Make installation idempotent and non-destructive: detect Sentinel-owned content, report user-owned conflicts, and remove only Sentinel-owned files or configuration with `--remove`.
- Keep existing OpenCode command discovery behavior compatible while adding the edit/write enforcement path.
- Document supported agent targets, generated files, conflict behavior, and the trusted execution/security implications.

## Capabilities

### New Capabilities

- `agent-edit-write-guards`: Provision and remove Sentinel-owned Claude Code hooks and OpenCode plugin/event integrations that run quality checks after agent edit/write operations.

### Modified Capabilities

- None; no main capability specifications currently exist under `openspec/specs/`.

## Impact

- `sentinel integrate` CLI agent selection, help text, supported-agent diagnostics, and removal behavior.
- Integration abstractions and implementations under `src/main/java/dev/sentinel/application` and `src/main/java/dev/sentinel/domain/agent`.
- Project-local `.claude/` and `.opencode/` configuration/plugin files, with ownership markers and conflict-safe updates.
- Composition-root wiring, integration/CLI tests, fixture projects, README documentation, and native-image verification.
- No changes to user source files, existing unowned agent configuration, or the quality-gate command contract beyond invoking the existing JSON check.
