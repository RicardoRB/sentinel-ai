# Sentinel

> Sentinel is a quality gate orchestrator for AI coding agents.

## Why it exists

AI coding agents are good at writing code and optimistic about whether it works. Sentinel gives
a project one deterministic place to answer "is this change acceptable?": a set of **quality
gates** declared in `sentinel.toml`, run with one command, reported with a clear pass/fail and a
meaningful exit code, in text for humans or JSON for programs.

This first iteration is deliberately small: Java, Maven, and one gate (automated tests). Sentinel
also provides project-local integrations for Claude Code and OpenCode so file edits can trigger a
machine-readable quality check.

## Development setup

Requirements: **JDK 25**. Maven is provided by the wrapper. For a native binary you also need
GraalVM 25 (`native-image`).

```bash
export JAVA_HOME=/path/to/jdk-25
./mvnw package            # compiles, runs all tests, builds target/sentinel.jar
bin/sentinel --help       # launcher script for the jar
```

Optional native image (GraalVM 25 as `JAVA_HOME`):

```bash
./mvnw -Pnative -DskipTests native:compile   # produces target/sentinel
```

Stack: Java 25, Guice, Picocli, tomlj, Jackson, JUnit 5 + AssertJ. Sentinel has no Spring runtime dependency.

## Commands

| Command | Description |
|---|---|
| `sentinel --help` / `--version` | Usage and version |
| `sentinel detect` | Detect the Maven/Java project in the current directory (or nearest parent) |
| `sentinel init` | Create `sentinel.toml` |
| `sentinel check [--format text\|json]` | Run all enabled quality gates |
| `sentinel integrate <agent>` | Install or remove an agent integration |

Every command also accepts `-C <dir>` to run as if started in `<dir>`.

```text
$ sentinel detect

Project detected

Language:     Java
Build tool:   Maven
Framework:    Spring Boot
Project root: /Users/ricardo/projects/example
```

Without a supported project it prints `No supported project detected.` and exits with code 1.
Detection looks for a `pom.xml` and marks the project as Spring Boot when the pom references
`org.springframework.boot`. No other languages or build tools are supported yet.

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Success / all quality gates passed |
| 1 | At least one quality gate failed (`detect`: no supported project) |
| 2 | Sentinel could not run: bad usage, missing/invalid `sentinel.toml`, no project, no enabled gates |

## `sentinel init`

When `sentinel.toml` does not exist, `sentinel init` launches a setup wizard that selects one or
more agent integrations and one quality gate. The integration choices are `none`, `opencode`, and `claude-code`;
the gate choices are the supported quality-gate registry entries (`tests`, `compile`,
`architecture`, `command`, `archunit`, `checkstyle`, `spotbugs`, and `sonar`).

For automation, provide both selections explicitly:

```bash
sentinel init --integration none --gate tests
sentinel init --integration claude-code --gate compile
sentinel init --integration opencode,claude-code --gate tests,compile
sentinel init --integration none --gate architecture --architecture hexagonal
```

In a terminal, both the integration and quality-gate selectors use checkboxes: arrow keys move,
Space toggles the highlighted option, and Enter confirms. In piped/non-TTY input, enter multiple
option numbers separated by spaces (for example `2 3`) and press Enter. The `none` option is
mutually exclusive with agent integrations.

Selecting `architecture` or `archunit` also asks for a Layered, Hexagonal, or Clean architecture
style and creates a minimal `ArchitectureTest.java` when one is missing. Existing architecture
tests are preserved and reported instead of overwritten.

The wizard detects whether the selected Maven wrapper or system Maven is available and reports
missing tooling with remediation guidance. It does not install tools or run the gate during init;
the selected gate runs later through `sentinel check` or an agent guard. An unavailable gate is
still written to `sentinel.toml` so the project can be configured before dependencies are installed.

The default tests selection writes:

```toml
version = 1

[quality-gates.tests]
enabled = true
command = "./mvnw test"
```

An existing `sentinel.toml` is never overwritten, and initialization does not prompt or change
integrations in that case. Invalid or incomplete interactive input exits without creating partial
configuration. Selected integrations use the same ownership markers and conflict protection as
`sentinel integrate`.

### Configuration

Each gate lives under `[quality-gates.<name>]` with `enabled` (default `true`) and `command`.
`command` is either a string or an array of strings. Repository-defined commands are trusted code
execution: they run with the current user's privileges in the project root, directly as an argument
list and never through a shell:

```toml
command = "./mvnw test"
command = ["./mvnw", "test", "-Dtest=A, B"]   # exact arguments, no splitting
```

The quality-gate registry accepts the listed gate IDs. Gate execution still depends on the
project's configured command and installed tools; enabling an unknown gate is an error rather than
a silent skip. Running with no enabled gates is also an error, so a pass always means something
was actually checked.

## `sentinel check`

```text
$ sentinel check

Sentinel

✓ tests        PASSED    4.21s

Quality gate: PASSED
```

On failure Sentinel also prints the command and the last lines of its stdout/stderr:

```text
✗ tests        FAILED    3.87s

Quality gate: FAILED

Command:
./mvnw test
```

All enabled gates run even if an earlier one fails; the result is the aggregate.

## JSON output

`sentinel check --format json` writes **only** JSON to stdout (diagnostics go to stderr):

```json
{
  "status" : "FAILED",
  "project" : { "language" : "JAVA", "buildTool" : "MAVEN", "framework" : "SPRING_BOOT" },
  "checks" : [ {
    "name" : "tests",
    "status" : "FAILED",
    "command" : "./mvnw test",
    "exitCode" : 1,
    "durationMs" : 3847,
    "stdout" : "...",
    "stderr" : "..."
  } ]
}
```

If Sentinel cannot run at all (exit code 2) stdout is `{"status": "ERROR", "error": "..."}`.
`framework` is `NONE` when no framework is recognised.

## `sentinel integrate`

Sentinel supports `opencode` and `claude-code` integrations. Installation is project-local,
idempotent, and ownership-marked:

```text
sentinel integrate claude-code
sentinel integrate opencode
sentinel integrate claude-code --remove
sentinel integrate opencode --remove
sentinel integrate                 # prompts to choose an agent
```

If the agent argument is omitted, Sentinel presents a numbered selection for `opencode` or
`claude-code`. Invalid or missing input exits without changing project files; scripts can avoid the
prompt by supplying the agent identifier explicitly.

Claude Code creates `.claude/settings.json` and the executable
`.claude/hooks/sentinel-edit-write`. Its `PostToolUse` hook matches `Edit` and `Write` tools and
runs `sentinel check --format json` after the operation. OpenCode retains the existing
`.opencode/commands/sentinel-check.md` command and additionally creates
`.opencode/plugins/sentinel-edit-write.js`, whose `tool.execute.after` handler checks `edit` and
`write` tools. A failed or unavailable check is returned to the host agent as a non-success result
with the Sentinel output.

Generated files contain Sentinel ownership markers. Existing user-owned targets are never
overwritten; installation reports a conflict and does not enable a partial integration. `--remove`
deletes only Sentinel-owned generated files (the existing OpenCode command is also removed when it
is Sentinel-owned) and preserves unrelated agent configuration. These hooks and plugins execute
repository-local configuration with the user's privileges, so only enable them in repositories
you trust.

## Security

**`sentinel.toml` is code.** `sentinel check` executes whatever `command` the repository
configures, with your user's privileges. A malicious or compromised repository (or pull request
that edits `sentinel.toml`) can run arbitrary programs on the machine that runs Sentinel. Only run
`sentinel check` on repositories you trust, and treat changes to `sentinel.toml` like changes to a
build script or CI pipeline. Do not run it on untrusted PRs outside a sandbox.

What Sentinel does to limit the blast radius:

- Commands are executed as an **argument vector, never through a shell**. `;`, `&&`, `|`,
  `$(...)`, globbing and redirection have no special meaning, so a command string cannot chain
  extra commands by accident. A string `command` is split on whitespace/quotes only.
- This is **not** a sandbox: `./mvnw` itself runs arbitrary build code, and so does any configured program.
- The child process gets no stdin and runs in the project root.
- Process execution sits behind the `CommandExecutor` interface, so predefined, safer
  integrations (fixed built-in commands, allow-lists, sandboxed execution) can replace free-form
  commands later without touching the gates.

## Architecture

```text
dev.sentinel
├── cli/             Picocli commands, text/JSON renderers, exit codes
├── application/     ProjectDetector, InitService, CheckService, QualityGateFactory, QualityGateRunner
├── domain/          Project, QualityGate, MavenTestGate, GateResult, CheckReport,
│                    SentinelConfiguration, and the ports CommandExecutor / SentinelConfigurationReader
└── infrastructure/  ProcessCommandExecutor (ProcessBuilder), TomlConfigurationReader (tomlj)
```

`cli → application → domain`, and `infrastructure` implements the ports defined in `domain`, so the
domain never touches `ProcessBuilder` or a TOML library. The composition root wires the pieces
explicitly; the native entry point uses the same constructor graph without reflective startup.

`QualityGate` (`name()`, `execute(Project)`) is the extension point. `QualityGateFactory` maps
configuration entries to gates; today only `tests` → `MavenTestGate`, whose command always comes
from the configuration.

Tests: unit tests per layer, plus `SentinelCliIntegrationTest`, which runs `init` and `check`
end to end against a small Maven fixture project (`src/test/resources/fixtures/maven-project`,
with a stub `mvnw` so it stays fast and offline).

## Roadmap

- More built-in gates (architecture rules, static analysis, SonarQube) behind `QualityGate`
- Predefined safe gate integrations instead of free-form commands; command timeouts
- Gradle and other languages
- Additional integrations with AI coding agents and MCP
- Published native binaries
