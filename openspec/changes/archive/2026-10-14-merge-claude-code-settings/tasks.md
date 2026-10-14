# Tasks

## 1. Merge Claude Code settings during installation

- [x] 1.1 Parse valid Claude settings JSON and merge Sentinel hook registrations without dropping unrelated values; add tests for existing settings, existing hooks, idempotent installs, unsupported JSON shapes, and malformed JSON, verifying all conflict cases leave every integration target unchanged.
- [x] 1.2 Preserve existing Sentinel-owned settings refresh and optional guard behavior while using merged settings; verify the Claude integration and guard integration test suites pass.

## 2. Remove Sentinel entries safely and document behavior

- [x] 2.1 Remove only Sentinel hook registrations and ownership metadata during Claude integration removal, retaining unrelated entries and the settings file; add tests for user hooks, empty resulting settings, and guard removal, verifying unrelated content remains and only Sentinel-owned standalone files are deleted.
- [x] 2.2 Update `website/src/content/docs/integrations/claude-code.mdx` to describe merging existing settings, safe removal, malformed-settings conflicts, and preservation behavior; verify the documentation accurately matches the integration contract.
- [x] 2.3 Run the focused Claude/OpenCode integration tests and `./verify-quality.sh`; verify all checks pass and OpenCode's existing conflict behavior remains unchanged.
