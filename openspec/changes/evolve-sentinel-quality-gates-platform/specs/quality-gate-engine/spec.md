## Purpose

Give Sentinel a deterministic, observable engine for running configured quality gates and returning useful results to both developers and automated coding agents.

## ADDED Requirements

### Requirement: Deterministic gate orchestration
Sentinel SHALL resolve enabled gates in deterministic order, execute every enabled gate even when an earlier gate fails, aggregate all results, and compute an overall status.

#### Scenario: Multiple gates with a failure
- **WHEN** more than one enabled gate is configured and one gate fails
- **THEN** all enabled gates run, each result is retained, and the overall status is failed

### Requirement: Process result capture
Each executed gate SHALL report its identifier, status, exit code, duration, stdout, stderr, and optional summary without invoking arbitrary shell wrappers.

#### Scenario: External command failure
- **WHEN** a configured command exits non-zero
- **THEN** Sentinel records a failed result with the exit code and captured output instead of crashing

### Requirement: Maven test gate
The tests gate SHALL run the project's Maven Wrapper when available and otherwise use Maven, with a missing executable or execution failure reported as an execution error or failed gate as appropriate.

#### Scenario: Wrapper preferred
- **WHEN** a Maven project contains an executable wrapper
- **THEN** the tests gate invokes the wrapper in the project root

### Requirement: Check command contract
`sentinel check` SHALL return exit code 0 when all enabled gates pass, 1 when at least one gate fails, and 2 for Sentinel, configuration, or execution errors.

#### Scenario: Successful check
- **WHEN** all enabled gates pass
- **THEN** the command reports a passed quality gate and exits 0

#### Scenario: Invalid check setup
- **WHEN** configuration is missing, malformed, or contains no runnable enabled gate
- **THEN** the command reports an actionable error and exits 2

### Requirement: Human and JSON output
The check command SHALL provide readable human output and a versioned JSON representation whose stdout contains JSON only and whose diagnostics are sent to stderr.

#### Scenario: JSON result
- **WHEN** a user runs `sentinel check --format json`
- **THEN** stdout is valid schema-versioned JSON containing project, overall status, and gate results without ANSI escape sequences
