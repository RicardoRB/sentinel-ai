# Proposal

## Why

Running `sentinel init` on Sentinel's own repository left a `pom.xml` that Maven refused to read (`Unrecognised tag: 'mutationThreshold'`). Two things went wrong. Plugins went into the first `</plugins>` in the file, which was inside a profile. The strict preset's PIT snippet also put `<mutationThreshold>` outside `<configuration>`. The insertion bug is fixed in PR #39. The underlying gap remains: init writes `pom.xml` without checking the result, and no test covers every snippet against Maven's model. A bad snippet, or an edit in the wrong place, therefore reaches the user's build undetected.

## What Changes

- Init checks the edited `pom.xml` in memory before writing it:
  - The document must be well-formed XML.
  - Every inserted declaration must be a direct child of `project/build/plugins` or `project/dependencies`.
  - Every element directly under an inserted `<plugin>` or `<dependency>` must be one Maven allows there.
  - If any check fails, init writes nothing, reports which tool caused it, and rolls back the other init changes as setup failures do today.
- Updating a managed plugin's version only touches the `<version>` of that plugin's own declaration. Today a regex can jump from one plugin's `<artifactId>` to a later plugin's `<version>`. If the declaration can't be found exactly, init refuses instead of editing nearby text.
- A test matrix applies every Maven gate, under no preset and under every quality preset, to a set of representative `pom.xml` fixtures. It checks each result with Maven's strict model reader (test scope only) and asserts that each declaration lands in the project-level section.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `interactive-init-setup`: adds requirements next to the existing "Maven tool configuration generation" requirement, which stays unchanged. Generated declarations go in the project-level build and dependencies. The edited `pom.xml` must be valid before it is written, and a failed check blocks the write and triggers rollback. Version updates are limited to the matching declaration.

## Impact

- **Code:**
  - `infrastructure/init`: `PomToolConfigurator`, `PomElements`, `PomXml`, `PresetPluginSnippets`, and a new in-process pom validator.
  - Application init flow: it already rolls back on `SentinelException`, so the failed-check path reuses it.
- **Dependencies:** `org.apache.maven:maven-model` at test scope only. Nothing is added to the runtime or the native image. The validator uses the JDK's SAX parser with DTDs and external entities disabled.
- **Native image:** the JDK XML parser has to work in the native executable. The native smoke test path covers it, and any metadata it needs goes into `reachability-metadata.json`.
- **Behavior:** a `pom.xml` that init would previously have corrupted now produces an init error with nothing written. Valid edits are unchanged byte for byte.
- **Docs:** the init page under `website/` describes the new failure mode.
- **Depends on:** PR #39 (`PomElements` and `PomXml`).
- **Non-goals (follow-ups):**
  - Checking at init time that external gate tools such as `gitleaks` are installed, and rejecting unfilled placeholders such as `<TARGET_URL>`.
  - A CI job that runs init on real projects.
  - Running `./mvnw validate` after writing.
  - Checking for existing declarations only in the project-level sections.
  - Gradle build files.
