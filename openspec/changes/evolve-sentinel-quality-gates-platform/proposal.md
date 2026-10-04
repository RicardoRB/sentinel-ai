## Why

Sentinel currently provides a small Java/Maven quality-gate CLI, but its implementation and dependency choices do not yet satisfy the product's native, framework-independent architecture or provide a path to agent integration and iterative verification. This change establishes the six-step evolution from the native CLI foundation through a language-agnostic quality-gate platform while preserving working behavior at every step.

## What Changes

- Remove Sentinel's runtime dependency on Spring Boot and standardize the application on Java 25, Maven, Guice, Picocli, TOML, JUnit 5, AssertJ, and GraalVM Native Image.
- Complete and harden the native CLI foundation: project detection, initialization, version/help output, typed configuration, and native-executable verification.
- Expand quality-gate execution with deterministic orchestration, process results, Maven tests, configurable commands, exit codes, human output, and a stable JSON contract.
- Add agent-agnostic integration abstractions, beginning with OpenCode integration and diagnostics through `sentinel integrate` and `sentinel doctor`.
- Add built-in and optional external quality gates for compilation, tests, architecture, Checkstyle, SpotBugs, and Sonar without installing tools automatically.
- Add bounded agentic quality loops with structured feedback, timeouts, terminal states, and Git safety checks.
- Evolve detection, gates, profiles, policies, and agent adapters to support multiple languages, build tools, monorepos, and agents while retaining Java/Maven support.
- Require tests and native executable verification after each sequential iteration; do not advance an iteration until the preceding one is complete.

## Capabilities

### New Capabilities

- `native-cli-foundation`: Framework-independent Java 25 CLI foundation, project detection, initialization, typed TOML configuration, and native executable behavior.
- `quality-gate-engine`: Deterministic quality-gate execution, process capture, gate results, exit codes, human output, and versioned JSON output.
- `agent-integration`: Agent-agnostic adapters, OpenCode integration/removal, diagnostics, and agent-facing result contracts.
- `real-quality-gates`: Built-in and optional external quality-gate registry, command configuration, tool diagnostics, and policy-ready results.
- `agentic-quality-loop`: Bounded agent-driven verification loops with structured feedback, safety controls, and terminal states.
- `multi-project-platform`: Multi-language/project detection, monorepo support, language-specific gates, agent adapters, profiles, policies, and versioned APIs.

### Modified Capabilities

- None; no existing OpenSpec capability specifications are present. The current CLI behavior will be preserved and extended through the new capability contracts.

## Impact

- `pom.xml` and the Java dependency graph, including removal of Spring Boot from Sentinel itself.
- CLI commands, output formats, exit-code behavior, configuration schema, and native-image metadata.
- Layered packages under `src/main/java` and corresponding unit, integration, fixture, and native-executable tests.
- New agent integration, quality-tool process execution, project discovery, profile/policy, and loop components.
- Documentation and build/verification workflows; implementation must not modify user source code, commit automatically, or execute arbitrary commands through a shell.
