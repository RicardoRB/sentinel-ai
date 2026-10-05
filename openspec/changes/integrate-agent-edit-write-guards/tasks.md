## 1. Integration contracts and shared guard runner

- [x] 1.1 Define the shared owned-artifact and guard-runner contract for project-local edit/write integrations, including ownership/version markers, changed paths, conflict states, and removal semantics.
- [x] 1.2 Implement the guard runner that invokes `sentinel check --format json` from the detected project root without shell interpolation and forwards the JSON result with the correct success/failure exit status.
- [x] 1.3 Add unit tests for successful checks, failed checks, execution errors, project-root working directories, machine-readable output, and nonzero propagation.

## 2. Claude Code integration

- [x] 2.1 Implement the Claude Code integration adapter for project-local post-edit/write hooks and register the `claude-code` agent identifier.
- [x] 2.2 Generate the Claude Code hook configuration and Sentinel-owned executable entry point with the supported edit/write event filters and stable format marker.
- [x] 2.3 Implement Claude Code install, already-present, conflict, partial-install, remove, and not-found behavior without overwriting unrelated `.claude` content.
- [x] 2.4 Add filesystem fixture tests that verify hook format, ownership markers, event selection, failure feedback, idempotency, conflict preservation, and safe removal.

## 3. OpenCode edit/write plugin integration

- [x] 3.1 Implement the OpenCode plugin or supported edit/write event handler using the shared guard runner and a distinct ownership marker from the existing command integration.
- [x] 3.2 Extend `sentinel integrate opencode` to install both the existing command discovery artifact and the edit/write guard, with preflight conflict handling and no partial success.
- [x] 3.3 Extend OpenCode removal to remove only the Sentinel-owned edit/write guard while preserving unowned content and retaining the existing command behavior under its documented lifecycle.
- [x] 3.4 Add OpenCode fixture tests for event handling, JSON feedback, failed checks, repeated installation, conflicts, removal, and compatibility with the existing command.

## 4. CLI wiring and user documentation

- [x] 4.1 Update integration composition-root wiring, CLI parameter descriptions, supported-agent error messages, and integration result reporting for Claude Code and OpenCode.
- [x] 4.2 Document generated paths, supported edit/write events, post-event timing, check failure behavior, conflict/removal rules, and trusted repository execution in the README.
- [x] 4.3 Add CLI integration tests covering both agent identifiers, unknown agents, project detection failures, `--remove`, and unchanged existing OpenCode command behavior.
- [x] 4.4 Add interactive agent selection when `sentinel integrate` has no positional agent, including invalid-input and EOF handling tests.

## 5. Verification and release safety

- [x] 5.1 Run the complete JVM test suite and verify architecture boundaries remain intact.
- [x] 5.2 Build the native executable and smoke-test `sentinel integrate claude-code`, `sentinel integrate opencode`, and their removal paths against temporary fixture projects.
- [x] 5.3 Validate generated hook/plugin files for ownership markers, shell-free invocation, no source-file edits, conflict preservation, and valid Sentinel JSON feedback before marking the change complete.
