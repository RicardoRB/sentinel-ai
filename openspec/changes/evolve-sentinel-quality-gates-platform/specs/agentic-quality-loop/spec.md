## Purpose

Provide a bounded verification loop in which an external coding agent can receive structured quality feedback and retry work safely without Sentinel becoming an autonomous code modifier.

## ADDED Requirements

### Requirement: Bounded quality loop
`sentinel loop` SHALL enforce configured maximum iterations and timeout limits, distinguish successful and terminal failure states, and never retry indefinitely.

#### Scenario: Failure then success
- **WHEN** an agent attempt fails quality gates and a subsequent attempt passes within limits
- **THEN** the loop returns a passed terminal state with the gate history available

#### Scenario: Maximum iterations
- **WHEN** quality gates continue failing through the configured iteration limit
- **THEN** the loop stops with `MAX_ITERATIONS_REACHED` and does not start another attempt

### Requirement: Structured feedback
Failed gate results SHALL be returned to the agent in structured form including gate identity, message, status, and relevant output.

#### Scenario: Agent receives failures
- **WHEN** a quality check fails during a loop
- **THEN** the next agent request includes machine-readable feedback for every failed gate

### Requirement: Git safety
Before starting a loop Sentinel SHALL detect repository and branch state and report uncommitted changes, and SHALL never automatically commit, reset, stash, or delete user changes.

#### Scenario: Dirty repository
- **WHEN** a loop starts with uncommitted changes
- **THEN** Sentinel reports the state and continues or refuses according to explicit configuration, without altering the changes

### Requirement: Agent and execution failures
The loop SHALL distinguish agent errors, timeouts, and quality-gate execution errors from ordinary failed quality results.

#### Scenario: Agent timeout
- **WHEN** an agent exceeds the configured timeout
- **THEN** the loop terminates with `TIMEOUT` and does not silently retry
