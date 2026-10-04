## Purpose

Allow Sentinel to integrate with coding agents through explicit adapters while keeping quality-gate execution and result contracts independent of any specific agent.

## ADDED Requirements

### Requirement: Agent adapter boundary
Sentinel SHALL expose an agent adapter boundary that identifies supported agents and keeps agent-specific integration logic separate from project detection and quality-gate execution.

#### Scenario: Unsupported agent
- **WHEN** a user requests integration for an unknown agent
- **THEN** Sentinel reports the supported choices and exits with an actionable error

### Requirement: OpenCode integration
Sentinel SHALL support `sentinel integrate opencode` and SHALL detect integration points, avoid destructive overwrites, and clearly report files or settings changed.

#### Scenario: New OpenCode integration
- **WHEN** integration is requested for a detected project with no existing Sentinel integration
- **THEN** Sentinel creates only the required integration artifacts and reports the changes

#### Scenario: Existing OpenCode configuration
- **WHEN** integration encounters existing configuration that would be overwritten
- **THEN** Sentinel preserves it and requests explicit resolution rather than silently replacing it

### Requirement: Integration removal
Sentinel SHALL support `sentinel integrate opencode --remove` and SHALL remove only Sentinel-owned integration content.

#### Scenario: Remove owned integration
- **WHEN** removal is requested for an existing Sentinel integration
- **THEN** Sentinel removes Sentinel-owned content and preserves unrelated configuration

### Requirement: Diagnostics
`sentinel doctor` SHALL check project detection, configuration, Maven and wrapper availability, enabled gates, agent integration, and native capabilities, reporting each finding as OK, warning, or error.

#### Scenario: Doctor on incomplete setup
- **WHEN** doctor runs on a project with missing or invalid prerequisites
- **THEN** it reports each issue with a useful remediation and exits according to the presence of errors
