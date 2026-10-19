# AGENTS.md

## Architecture

- Organize production packages layer-first as `domain/<feature>`, `application/<feature>`, and `infrastructure/<feature>`.
- CLI commands are inbound adapters under `infrastructure/cli/<feature>`; shared Picocli plumbing lives in `infrastructure/cli`.
- Dependency direction is `infrastructure.cli → application → domain`; outbound adapters under `infrastructure/<feature>` implement domain ports. CLI adapters must not depend on concrete outbound adapters or perform filesystem/process work.
- Keep domain models free of I/O and framework implementations; application orchestrates domain contracts without depending on infrastructure. `ArchitectureBoundaryTest` enforces bytecode boundaries, placement, and feature dependencies.
- Raw-key terminal input uses the `RawTerminal` port and POSIX adapter; do not launch `stty` or another process from CLI code. Route filesystem checks through application use cases and domain ports.
- `QualityGate` is the extension point; update `QualityGateFactory` and `sentinel.toml` together when adding a gate.
- `dev.sentinel.config.SentinelComponent` wires Dagger at compile time; preserve the explicit constructor graph for native-image compatibility.
- Native images contain only classes seen at build time. Do not call APIs that build classes at runtime: `MethodHandleProxies`, `MethodHandles.Lookup#defineHiddenClass` / `defineClass`, and similar (`java.lang.classfile`, custom class loaders). `NativeImageCompatibilityTest` rejects them. Register any `java.lang.reflect.Proxy` interface, reflective type, or FFM downcall in `src/main/resources/META-INF/native-image/dev.sentinel/sentinel-cli/reachability-metadata.json`.

## Code Style

- Use constructor injection; do not add field injection.
- Spotless with Google Java Format is the Java formatter; run `./mvnw spotless:apply` to format and `./mvnw spotless:check` to verify. Spring/JPA and code-generation workflows are not configured.
- Fix the underlying cause of compiler and static-analysis findings. Do not add `@SuppressWarnings`, `@SuppressFBWarnings`, `NOPMD`, exclusions, disable checks, or lower thresholds to make verification pass. Preserve behavior and test coverage when refactoring. If a finding appears to be a framework limitation or false positive, explain it with evidence and request explicit review before introducing a suppression or workaround.
- Commands from `sentinel.toml` run from the project root as argument vectors, never through a shell.
- When implementing or changing operational behavior, add useful JUL diagnostics at meaningful decision and execution boundaries when logs would aid troubleshooting. Keep logs invocation-scoped, stderr-only, and safe: never log secrets, prompts, configuration or environment contents, raw argument vectors, or captured process output.

## Testing

- JDK 25 and Maven wrapper are required: `./mvnw -q test`.
- Run one test with `./mvnw -Dtest=ClassName#methodName test`.
- CLI integration tests use the offline fixture `src/test/resources/fixtures/maven-project` and its stub `mvnw`.
- Use `FakeCommandExecutor` for process tests. Existing test names use descriptive camelCase.
- `NativeInitTerminalSmokeTest` drives a built executable's `init` wizard through a pseudo-terminal and is skipped unless `-Dsentinel.native.binary` is set: `./mvnw -Dtest=NativeInitTerminalSmokeTest -Dsentinel.native.binary=target/sentinel test` after `native:compile`.
- Testcontainers is not configured; do not invent or claim that check.

## Security

- Treat `sentinel.toml` as executable trusted code; do not run it on untrusted repositories without isolation.
- Never log secrets or full request/response bodies; use environment variables for credentials.
- Integration files are ownership-marked: preserve conflict handling, rollback, and user-owned files.

## Verification (non-negotiable)

- Do not modify anything under `/config`.
- Run `sentinel check --profile pre-push` before finishing. Gates and profiles are defined in `sentinel.toml`; `sentinel check --profile ci` runs every gate.
- Claude Code and OpenCode edit/write hooks must invoke `sentinel check` through the Sentinel integration: `.claude/hooks/sentinel-edit-write` (registered in `.claude/settings.json`) and `.opencode/plugins/sentinel-edit-write.js`.
- `check --format json` must write only JSON to stdout; diagnostics go to stderr.
- CLI changes must update the matching documentation pages under `website/`.
- Changes to `sentinel init` options or behavior must update the agent skills under `skills/` in the same pull request; `AgentSkillsConsistencyTest` checks options and versions, not wording.
- `./mvnw package` builds `target/sentinel.jar`; native builds require GraalVM 25 and `./mvnw -Pnative -DskipTests native:compile`.
