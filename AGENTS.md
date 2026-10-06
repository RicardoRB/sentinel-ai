# AGENTS.md

## Architecture

- Organize production packages layer-first as `domain/<feature>`, `application/<feature>`, and `infrastructure/<feature>`.
- CLI commands are inbound adapters under `infrastructure/cli/<feature>`; shared Picocli plumbing lives in `infrastructure/cli`.
- Dependency direction is `infrastructure.cli → application → domain`; outbound adapters under `infrastructure/<feature>` implement domain ports. CLI adapters must not depend on concrete outbound adapters or perform filesystem/process work.
- Keep domain models free of I/O and framework implementations; application orchestrates domain contracts without depending on infrastructure. `ArchitectureBoundaryTest` enforces bytecode boundaries, placement, and feature dependencies.
- Raw-key terminal input uses the `RawTerminal` port and POSIX adapter; do not launch `stty` or another process from CLI code. Route filesystem checks through application use cases and domain ports.
- `QualityGate` is the extension point; update `QualityGateFactory` and `sentinel.toml` together when adding a gate.
- `dev.sentinel.config.SentinelComponent` wires Dagger at compile time; preserve the explicit constructor graph for native-image compatibility.

## Code Style

- Use constructor injection; do not add field injection.
- Spotless with Google Java Format is the Java formatter; run `./mvnw spotless:apply` to format and `./mvnw spotless:check` to verify. Spring/JPA and code-generation workflows are not configured.
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
- Run `./verify-quality.sh` before finishing. It runs ArchUnit, tests, Spotless, Checkstyle, SpotBugs, JaCoCo, and enforces ≥80% JaCoCo instruction coverage.
- Claude Code and OpenCode edit/write hooks must invoke `verify-quality.sh`. The native V2 plugin is `plugins/sentinel-jacoco.ts`, loaded by `opencode.jsonc`.
- `check --format json` must write only JSON to stdout; diagnostics go to stderr.
- CLI changes must update the matching documentation pages under `website/`.
- `./mvnw package` builds `target/sentinel.jar`; native builds require GraalVM 25 and `./mvnw -Pnative -DskipTests native:compile`.
