## 1. Iteration 1 — Native CLI foundation

- [ ] 1.1 Capture characterization tests for current help, version, detect, init, configuration, output, and exit-code behavior.
- [ ] 1.2 Audit and update the Maven build to Java 25, Guice, Picocli, TOML, JUnit 5, AssertJ, and GraalVM Native Image with no Spring Boot dependency in Sentinel.
- [ ] 1.3 Establish explicit layered packages and a constructor-injected composition root without domain dependencies on CLI, framework, process, or filesystem implementations.
- [ ] 1.4 Complete typed project detection for Java/Maven, Spring Boot metadata, Maven Wrapper availability, unsupported projects, and nearest supported project root.
- [ ] 1.5 Complete typed TOML configuration parsing and safe `sentinel init` behavior, including refusal to overwrite existing files.
- [ ] 1.6 Verify `--help`, `--version`, `detect`, and `init` from the JVM and native executable, then commit the passing iteration as one incremental checkpoint.

## 2. Iteration 2 — Quality-gate engine

- [ ] 2.1 Define immutable command, process-result, gate-status, gate-result, and aggregated-check contracts.
- [ ] 2.2 Implement explicit argument-list process execution with working directories, stdout/stderr capture, exit codes, durations, and execution errors.
- [ ] 2.3 Implement deterministic enabled-gate resolution and failure continuation.
- [ ] 2.4 Implement the Maven tests gate with Maven Wrapper preference and system Maven fallback.
- [ ] 2.5 Extend configuration for enabled gates and trusted project-local commands without shell execution.
- [ ] 2.6 Implement `sentinel check` exit codes, human rendering, JSON-only stdout, stderr diagnostics, and schema version 1.
- [ ] 2.7 Add executor, orchestration, output, configuration, failure-continuation, and native smoke tests; build native and commit the passing iteration.

## 3. Iteration 3 — Agent integration

- [ ] 3.1 Define the agent adapter and agent-facing request/result boundaries independent of the gate engine.
- [ ] 3.2 Implement OpenCode integration-point discovery and non-destructive conflict handling using filesystem fixtures.
- [ ] 3.3 Implement `sentinel integrate opencode` with explicit change reporting and ownership markers.
- [ ] 3.4 Implement `sentinel integrate opencode --remove` to remove only Sentinel-owned content.
- [ ] 3.5 Implement `sentinel doctor` checks for configuration, project, Maven, wrapper, gates, integration, and native capabilities.
- [ ] 3.6 Test integration, removal, conflicts, unknown agents, doctor diagnostics, JSON contract, and the native executable; commit the passing iteration.

## 4. Iteration 4 — Real quality gates

- [ ] 4.1 Add the typed gate registry and built-in compile, tests, architecture, and command gates.
- [ ] 4.2 Add configuration validation for unknown gate IDs, disabled gates, commands, deterministic ordering, and skipped output.
- [ ] 4.3 Add optional ArchUnit, Checkstyle, SpotBugs, and Sonar process integrations without automatic installation.
- [ ] 4.4 Define unavailable-tool diagnostics and distinguish unavailable, failed, skipped, and execution-error outcomes.
- [ ] 4.5 Add gate-specific fake-executor tests, malformed-configuration tests, JSON/human parity tests, and native tests.
- [ ] 4.6 Build and exercise the native executable for all applicable commands, then commit the passing iteration.

## 5. Iteration 5 — Agentic quality loop

- [ ] 5.1 Extend the agent contract for bounded task execution and define loop configuration and terminal states.
- [ ] 5.2 Implement iteration limits, timeout enforcement, success handling, and explicit agent/gate error handling.
- [ ] 5.3 Implement structured failed-gate feedback for the next agent request and final loop output.
- [ ] 5.4 Implement Git repository, branch, and dirty-state inspection without automatic commit, reset, stash, or deletion.
- [ ] 5.5 Implement `sentinel loop` with safe terminal behavior and no silent retries.
- [ ] 5.6 Add fake-agent tests for success, retry success, multiple failures, maximum iterations, timeout, agent failure, Git state, and native execution; commit the passing iteration.

## 6. Iteration 6 — Multi-project platform

- [ ] 6.1 Expand project descriptors and detection for Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust, and C# markers and build tools.
- [ ] 6.2 Implement deterministic multi-root and monorepo discovery, with gate commands scoped to their project roots.
- [ ] 6.3 Add language-specific gate registries while preserving the Java/Maven gate behavior.
- [ ] 6.4 Add Claude Code and Codex adapters through the existing agent boundary without gate-engine conditionals.
- [ ] 6.5 Implement named profiles, profile selection, separate policy evaluation, and strict-profile behavior.
- [ ] 6.6 Preserve and document the versioned JSON contract across languages, agents, projects, and policies.
- [ ] 6.7 Add mixed-repository, monorepo, adapter, profile, policy, backward-compatibility, and native end-to-end tests; commit the final passing iteration.

## 7. Final verification

- [ ] 7.1 Run the complete Maven test suite and native build from a clean checkout using the documented Java/GraalVM setup.
- [ ] 7.2 Smoke-test the native executable for every applicable command and verify stdout/stderr, exit codes, JSON validity, and no JVM runtime requirement.
- [ ] 7.3 Review documentation, security constraints, generated artifacts, and incremental commit history before release.
