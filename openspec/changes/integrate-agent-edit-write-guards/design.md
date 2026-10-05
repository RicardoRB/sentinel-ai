## Context

The repository already has an agent-integration boundary and an ownership-marked OpenCode command, but the CLI wiring currently registers only OpenCode and the Claude Code adapter is an identity placeholder. The existing quality contract is `sentinel check --format json`; integrations must call that contract from project-local agent configuration without routing through a shell or overwriting user content.

## Goals / Non-Goals

**Goals:**

- Add agent-specific integration implementations behind the existing integration capability boundary.
- Generate native Claude Code hook configuration and an OpenCode plugin/event handler for post-edit/write checks.
- Share a small, deterministic owned runner contract so both integrations invoke the same project-local Sentinel command and propagate machine-readable results.
- Make install, reinstall, conflict, and removal behavior testable with filesystem fixtures and CLI-level coverage.
- Preserve current OpenCode command discovery and the existing JSON report semantics.

**Non-Goals:**

- Running checks before an edit/write, changing agent file-edit semantics, or implementing an agent loop inside Sentinel.
- Modifying user source files, merging arbitrary user hook/plugin code, or automatically installing agent runtimes.
- Replacing the existing quality-gate engine or introducing a new output schema.

## Decisions

### 1. Keep agent integrations separate and compose them in the CLI

Add Claude Code and OpenCode implementations that share ownership, rendering, and process-invocation conventions but retain agent-specific generated formats. Register both through `IntegrationService` and update supported-agent diagnostics.

**Alternative considered:** Put both formats into one integration class. Rejected because agent configuration formats and event APIs evolve independently, and a single class would make conflict/removal logic harder to isolate.

### 2. Use post-event guards and the existing JSON check

Each generated guard runs after a successful applicable edit/write operation, from the project root, using the existing `sentinel check --format json` command. The wrapper passes the JSON result to the host agent and exits nonzero for failed/error results; it does not parse or rewrite source files.

**Alternative considered:** Run a pre-edit check. Rejected because it verifies stale state and cannot validate the file change that triggered the guard.

### 3. Use explicit ownership markers and atomic conflict checks

Generated standalone files carry a stable Sentinel marker and a format/version marker. If the target exists without matching ownership, installation returns `CONFLICT` and leaves it untouched. Removal checks ownership before deleting; managed-section updates, if required by a host format, use bounded marker-delimited sections and preserve all other content.

**Alternative considered:** Merge generated configuration into any existing file. Rejected because silently editing user-owned hooks or plugins is unsafe and makes reliable removal impossible.

### 4. Keep the wrapper shell-free where Sentinel controls execution

The generated entry point SHALL invoke Sentinel with an argument vector and a project-root working directory, consistent with the existing trusted-command security model. Host-specific configuration may point to the wrapper using the agent's supported command mechanism, but the wrapper must not interpolate event payloads into a shell command.

**Alternative considered:** Generate inline shell snippets. Rejected due to quoting, portability, and injection risks.

### 5. Treat OpenCode command and guard as independently owned artifacts

The existing `.opencode/commands/sentinel-check.md` remains supported. The new edit/write handler gets its own ownership marker and lifecycle so a conflict in one artifact does not overwrite or remove the other. Installation must avoid reporting the whole integration as successful if a required guard cannot be installed.

**Alternative considered:** Replace the command with a plugin-only integration. Rejected because it would break existing users and agent-facing command discovery.

## Risks / Trade-offs

- [Risk] Claude Code or OpenCode event configuration changes across versions. → Isolate generated templates, validate against fixtures, version ownership markers, and return a clear conflict/error rather than guessing.
- [Risk] A check after every write may be expensive. → Restrict invocation to applicable edit/write events and reuse the existing deterministic check; document the behavior rather than adding hidden throttling.
- [Risk] A project-local wrapper can be changed by repository users. → Mark generated content as trusted repository configuration, document the security model, and never treat the integration as a sandbox.
- [Risk] Partial installation could leave one agent artifact active without its runner. → Preflight target conflicts, write owned artifacts atomically where possible, and report failures with changed paths so removal/retry is explicit.

## Migration Plan

1. Add fixture-based format and lifecycle tests for Claude Code and OpenCode integrations.
2. Implement shared owned-runner generation and agent-specific configuration adapters.
3. Register both integrations, update CLI help/errors, and preserve the current OpenCode command tests.
4. Add documentation and run the full JVM test suite plus native compilation and integration smoke tests.

Rollback is removing the new owned artifacts with `--remove` and reverting the implementation; existing unowned agent files and the existing OpenCode command remain untouched.

## Open Questions

None. The exact host configuration syntax must be implemented against the supported Claude Code and OpenCode formats, but it does not change the observable requirements or architecture above.
