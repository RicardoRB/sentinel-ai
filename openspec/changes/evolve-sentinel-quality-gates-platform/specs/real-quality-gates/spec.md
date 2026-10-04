## Purpose

Make Sentinel useful for real projects by standardizing built-in and optional external quality gates while preserving its role as an orchestrator rather than reimplementing analysis tools.

## ADDED Requirements

### Requirement: Built-in gate registry
Sentinel SHALL provide typed, discoverable gates for compilation, tests, architecture, and configured external commands, and SHALL reject unknown gate identifiers clearly.

#### Scenario: Unknown gate
- **WHEN** configuration references an unsupported gate identifier
- **THEN** Sentinel reports the identifier and valid alternatives and exits with a configuration error

### Requirement: Optional analysis tools
Sentinel SHALL support optional ArchUnit, Checkstyle, SpotBugs, and Sonar integrations through external commands, SHALL report unavailable tools as warnings or gate errors, and SHALL never install tools automatically.

#### Scenario: Tool unavailable
- **WHEN** an enabled optional tool is not installed
- **THEN** Sentinel reports the missing tool and remediation without attempting installation

### Requirement: Configurable gate execution
Gate configuration SHALL allow enabling, disabling, and supplying trusted project-local commands while preserving deterministic ordering and structured results.

#### Scenario: Disabled gate
- **WHEN** a configured gate is disabled
- **THEN** it is not executed and its status is reported as skipped when included in output

### Requirement: Equivalent output
Human and JSON output SHALL expose equivalent gate statuses, durations, summaries, and overall policy-relevant results.

#### Scenario: Gate result serialization
- **WHEN** a check includes built-in and external gates
- **THEN** both output formats identify each gate and its outcome consistently
