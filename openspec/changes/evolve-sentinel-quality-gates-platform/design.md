## Context

The repository is a Java 25 Maven CLI with an existing detection, initialization, configuration, quality-gate, text/JSON reporting, and native-image path. The change is cross-cutting and must preserve the current command behavior while removing framework coupling and adding capabilities in six independently verifiable iterations. See `proposal.md` for motivation and the capability specs for observable contracts.

## Goals / Non-Goals

**Goals:**

- Keep a layered, native-image-friendly CLI with explicit composition and constructor-injected dependencies.
- Make process execution, gate results, configuration, agent adapters, and JSON output explicit domain contracts.
- Deliver each iteration as a complete vertical slice with unit tests, fixtures, native build, and native smoke tests before starting the next.
- Preserve Java/Maven behavior while making project and gate handling extensible to multiple roots and ecosystems.
- Keep all external command execution explicit as argument lists, with project-defined commands treated as trusted input and never routed through a shell.

**Non-Goals:**

- Implementing static-analysis engines, an LLM, or an AI agent inside Sentinel.
- Automatically installing tools, modifying user source code, or managing Git history.
- Delivering all future platform capabilities in one iteration or introducing network services.

## Decisions

### 1. Use explicit layered composition

Organize behavior into CLI, application, domain, infrastructure, and dependency-injection/composition-root layers. Domain records and interfaces will not depend on Picocli, Guice, Maven APIs, Spring, operating-system process APIs, or concrete filesystem APIs. The composition root will bind concrete infrastructure implementations and command handlers through constructor injection.

**Alternative considered:** retain framework-managed application services. Rejected because it increases reflection and native-image configuration, violates the framework-independent requirement, and obscures dependencies.

### 2. Replace shell strings with structured processes

Represent commands as immutable argument lists plus a working directory. The process adapter will capture stdout, stderr, exit code, duration, and execution errors. Gate implementations will select wrapper or system executables explicitly; no `sh -c`, `bash -c`, or equivalent wrapper will be used.

**Alternative considered:** execute configured strings through a shell for convenience. Rejected due to quoting ambiguity, portability problems, and unnecessary command-injection exposure.

### 3. Make gate registry and result schema the stable seam

A typed registry will resolve built-in and configured gates by ID and deterministic order. A shared immutable result model will feed human rendering, JSON serialization, policy evaluation, and agent feedback. JSON will carry an explicit schema version and will keep diagnostics off stdout.

**Alternative considered:** let each command define its own output DTOs. Rejected because it would make agent consumers and future language adapters depend on command-specific shapes.

### 4. Use adapters for agents and project-specific integrations

Agent integrations will implement a narrow adapter boundary, beginning with OpenCode. File changes will be ownership-marked or otherwise detectable so removal only deletes Sentinel-owned content. The gate engine will receive agent-independent requests/results and will not contain adapter-specific conditionals.

**Alternative considered:** encode OpenCode configuration directly in CLI commands. Rejected because it prevents later Claude Code/Codex support and makes safe removal difficult.

### 5. Model projects as a collection after the Java/Maven slice

Iteration 1 retains the single detected Java/Maven project contract. The platform phase will introduce a collection of project descriptors with root, language, build tool, framework metadata, and relevant markers. Gate requests will carry the target project root, allowing monorepos to execute commands in the correct directory.

**Alternative considered:** infer one language/build tool from repository root. Rejected because it cannot represent monorepos or mixed-language repositories.

### 6. Implement the roadmap as gated vertical slices

Tasks will be grouped into six phases: native foundation, quality engine, agent integration, real gates, bounded loop, and multi-project platform. Each phase ends with `./mvnw test`, `./mvnw -Pnative native:compile`, and native executable smoke tests for all applicable existing commands. Later phases may extend contracts but must preserve earlier JSON and exit-code behavior.

**Alternative considered:** implement all abstractions first and defer verification. Rejected because it would create a broad untestable migration and violate the sequential delivery constraint.

## Risks / Trade-offs

- [Risk] Removing Spring or changing dependency wiring can regress existing CLI behavior or native metadata. → Establish characterization tests before migration and run JVM plus native smoke tests at every phase.
- [Risk] Native Image may reject reflection used by Guice, Picocli, TOML, or serialization. → Prefer explicit bindings and models, generate only required metadata, and treat the native build as a required test target rather than a final release step.
- [Risk] External tools differ in availability, output, and exit semantics. → Keep integrations as process adapters, distinguish unavailable tools from failed gates, and test with deterministic fake executors.
- [Risk] Agent configuration formats can change or contain user-owned content. → Use fixture-based integration tests, ownership markers, conflict detection, and non-destructive removal.
- [Risk] A growing cross-language model can over-generalize Java assumptions. → Keep language/build detection and gate selection behind explicit registries and add one ecosystem at a time only after the Java contract remains green.
- [Risk] Long-running agent loops can consume resources or repeat unsafe work. → Enforce iteration and timeout limits, expose terminal states, inspect Git state, and never auto-commit/reset/stash/delete.

## Migration Plan

1. Capture current command, configuration, output, and exit-code behavior in tests; identify and remove Sentinel's framework dependencies without changing the public commands.
2. Complete the native foundation and verify native execution.
3. Add the gate engine and JSON contract; verify all existing commands plus `check` from the native binary.
4. Add OpenCode integration and doctor with filesystem fixtures; verify conflict and removal safety.
5. Add built-in/optional gate registry and diagnostics; verify missing tools and deterministic execution.
6. Add the bounded loop with fake agents and Git fixtures; verify every terminal state and native execution.
7. Add multi-project detection, profiles, policies, and additional adapters incrementally; verify Java/Maven backward compatibility and cross-language JSON.

Rollback at any phase is a source-level rollback to the last passing incremental commit. No migration should modify user project files except explicit `init`/integration commands, and those commands must preserve existing files on conflict.

## Open Questions

- None that change the current specifications or implementation approach. Exact OpenCode file locations and additional language marker edge cases can be selected during the corresponding phase while honoring the stated non-destructive and versioned contracts.
