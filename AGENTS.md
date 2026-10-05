# AGENTS.md

## Architecture

- Hexagonal boundary: `cli → application → domain`; `infrastructure` implements domain ports.
- `domain/` must not import `ProcessBuilder` or TOML. `ArchitectureBoundaryTest` enforces this rule.
- `QualityGate` is the extension point; update `QualityGateFactory` and `sentinel.toml` together when adding a gate.
- `config/SentinelModule` wires Guice; preserve the explicit constructor graph for native-image compatibility.

## Code Style

- Use constructor injection; do not add field injection.
- No Spotless, Spring/JPA, or code-generation workflow is configured. Preserve the existing style.
- Commands from `sentinel.toml` run from the project root as argument vectors, never through a shell.

## Testing

- JDK 25 and Maven wrapper are required: `./mvnw -q test`.
- Run one test with `./mvnw -Dtest=ClassName#methodName test`.
- CLI integration tests use the offline fixture `src/test/resources/fixtures/maven-project` and its stub `mvnw`.
- Use `FakeCommandExecutor` for process tests. Existing test names use descriptive camelCase.
- PIT, Testcontainers, and OWASP Dependency-Check are not configured; do not invent or claim those checks.

## Security

- Treat `sentinel.toml` as executable trusted code; do not run it on untrusted repositories without isolation.
- Never log secrets or full request/response bodies; use environment variables for credentials.
- Integration files are ownership-marked: preserve conflict handling, rollback, and user-owned files.

## Verification (non-negotiable)

- Do not modify anything under `/config`.
- Run `./verify-quality.sh` before finishing. It runs tests, Checkstyle, SpotBugs, JaCoCo, and enforces ≥80% JaCoCo instruction coverage.
- Claude Code and OpenCode edit/write hooks must invoke `verify-quality.sh`. The native V2 plugin is `plugins/sentinel-jacoco.ts`, loaded by `opencode.jsonc`.
- `check --format json` must write only JSON to stdout; diagnostics go to stderr.
- `./mvnw package` builds `target/sentinel.jar`; native builds require GraalVM 25 and `./mvnw -Pnative -DskipTests native:compile`.
