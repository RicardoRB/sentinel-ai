## Purpose

Evolve Sentinel into a language-agnostic platform that can discover multiple projects, apply profiles and policies, and integrate multiple coding agents without breaking Java/Maven workflows.

## ADDED Requirements

### Requirement: Multi-language project detection
Sentinel SHALL detect supported Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust, and C# projects using their documented marker files and SHALL report the associated build tools.

#### Scenario: Mixed repository
- **WHEN** a repository contains markers for projects in more than one supported language
- **THEN** Sentinel reports each detected project rather than collapsing the repository to one language

### Requirement: Monorepo support
Sentinel SHALL support multiple detected projects within one repository and SHALL associate gate execution with the relevant project root.

#### Scenario: Independent project roots
- **WHEN** a repository contains multiple project roots
- **THEN** detection and checks identify each root and do not run commands from an unrelated directory

### Requirement: Agent adapters
Sentinel SHALL support OpenCode, Claude Code, and Codex through the same agent-independent contract, without leaking adapter-specific behavior into the gate engine.

#### Scenario: Agent selection
- **WHEN** a supported agent is selected
- **THEN** Sentinel routes integration or loop operations through that adapter and preserves the same quality result contract

### Requirement: Profiles and policies
Sentinel SHALL support named gate profiles selectable from the CLI and SHALL evaluate gate execution separately from policies such as all tests passing or no critical analysis issues.

#### Scenario: Strict profile
- **WHEN** `check --profile strict` is requested
- **THEN** Sentinel executes the gates declared by the strict profile and evaluates the resulting policy

### Requirement: Language-specific gates
Sentinel SHALL provide per-ecosystem gate sets (for example Maven/ArchUnit/Checkstyle/SpotBugs for Java, type-check/ESLint/tests for TypeScript, pytest/ruff/mypy for Python, `go test`/`go vet` for Go, `cargo test`/`cargo clippy` for Rust) as process orchestrations of external tools.

#### Scenario: Unavailable ecosystem tool
- **WHEN** a gate's external tool is not installed
- **THEN** Sentinel reports it as unavailable with remediation and does not install it

### Requirement: Versioned machine contract
Machine-readable results SHALL include a stable `schemaVersion` and SHALL preserve the documented project, status, gate, and policy shape across supported languages and agents.

#### Scenario: Documented schema
- **WHEN** a consumer reads the project documentation
- **THEN** the JSON schema, its version policy, and field meanings are documented

#### Scenario: Cross-language JSON result
- **WHEN** a check runs for a non-Java project
- **THEN** its JSON result uses the same versioned top-level contract while reporting language-specific details where applicable
