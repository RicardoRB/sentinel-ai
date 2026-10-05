## Purpose

This capability gives AI coding agents a consistent, project-local Sentinel quality boundary after they edit or write files, while keeping installation reversible, conflict-safe, and compatible with user-owned agent configuration.

## ADDED Requirements

### Requirement: Claude Code edit/write guard integration

`sentinel integrate claude-code` SHALL create a Claude Code project integration that observes applicable file edit/write tool events and invokes the project-local Sentinel check using machine-readable output.

#### Scenario: Install Claude Code guard in an unconfigured project
- **WHEN** a supported project runs `sentinel integrate claude-code`
- **THEN** Sentinel creates the required project-local Claude Code hook configuration and owned executable entry point, reports the created paths, and configures the guard to run after applicable edit/write events

#### Scenario: Claude Code guard reports a failed check
- **WHEN** a Claude Code edit/write event completes and `sentinel check --format json` reports a failed or error quality result
- **THEN** the hook returns a non-success result containing the Sentinel result so Claude Code can surface the failure to the agent

### Requirement: OpenCode edit/write guard integration

`sentinel integrate opencode` SHALL provision an OpenCode plugin or equivalent supported event handler that observes applicable edit/write operations and invokes `sentinel check --format json` against the project root.

#### Scenario: Install OpenCode guard while preserving command discovery
- **WHEN** a supported project runs `sentinel integrate opencode`
- **THEN** Sentinel creates the edit/write guard in addition to the existing Sentinel-owned OpenCode command integration, without replacing an unowned command or plugin

#### Scenario: OpenCode guard returns check feedback
- **WHEN** an observed OpenCode edit/write operation completes and the Sentinel check fails or cannot run
- **THEN** the integration returns structured or textual failure feedback to OpenCode and uses a non-success outcome so the agent can stop or respond to the quality failure

### Requirement: Idempotent and ownership-safe installation

Integrations SHALL identify Sentinel-owned generated files or managed sections, SHALL be idempotent for matching owned content, and SHALL refuse to overwrite conflicting user-owned content.

#### Scenario: Reinstall an owned guard
- **WHEN** the requested agent integration already contains the current Sentinel-owned guard
- **THEN** Sentinel makes no duplicate changes and reports the integration as already present

#### Scenario: Encounter a conflicting target
- **WHEN** a required target path or managed configuration section exists without the Sentinel ownership marker or is incompatible with the expected guard
- **THEN** Sentinel preserves the existing content, reports a conflict, and does not enable a partial guard

### Requirement: Safe removal

`sentinel integrate <agent> --remove` SHALL remove only Sentinel-owned guard files or managed sections and SHALL preserve unrelated user-owned agent configuration.

#### Scenario: Remove an installed guard
- **WHEN** the project has a Sentinel-owned Claude Code or OpenCode edit/write guard
- **THEN** Sentinel removes that owned guard, retains the existing OpenCode discovery command unless it is also explicitly represented as owned integration content under the removal contract, and reports the removed paths

#### Scenario: Remove an absent or unowned guard
- **WHEN** no owned guard exists or the candidate content is user-owned
- **THEN** Sentinel reports not found or conflict respectively and does not delete the candidate content

### Requirement: Existing integration compatibility and documentation

The integration SHALL retain the existing `opencode` command-discovery behavior and SHALL document supported agent identifiers, generated paths, event timing, failure behavior, conflict handling, removal, and trusted execution implications.

#### Scenario: Use the existing OpenCode command
- **WHEN** a project uses the Sentinel-owned OpenCode command after the guard is installed
- **THEN** the command continues to invoke the existing JSON quality check contract without requiring the edit/write guard

#### Scenario: Request an unsupported agent
- **WHEN** `sentinel integrate` receives an agent identifier other than a supported Claude Code or OpenCode identifier
- **THEN** Sentinel exits with its existing error behavior and lists the supported identifiers without changing project files

### Requirement: Interactive agent selection

When no agent identifier is supplied to `sentinel integrate`, Sentinel SHALL present an interactive selection containing every supported agent, accept the selected option, and continue with the same integration behavior as an explicit agent argument.

#### Scenario: Select an agent when the argument is omitted
- **WHEN** a user runs `sentinel integrate` and selects OpenCode or Claude Code from the displayed options
- **THEN** Sentinel integrates the selected agent and reports the same changed, already-present, conflict, or removal result as `sentinel integrate <selected-agent>`

#### Scenario: Reject an invalid interactive selection
- **WHEN** a user runs `sentinel integrate` without an agent and enters an option that is not displayed
- **THEN** Sentinel reports an actionable selection error and does not modify project files

#### Scenario: Handle missing interactive input
- **WHEN** `sentinel integrate` is run without an agent and input reaches end-of-file before a selection is made
- **THEN** Sentinel exits with an error explaining that an agent argument or interactive selection is required and does not modify project files
