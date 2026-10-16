# Design

## Context

`PomToolConfigurator` (in `infrastructure/init`) builds the edited `pom.xml` as a string, one tool at a time. It inserts snippets with `PomXml`, which uses `PomElements` to find the project-level `build/plugins` and `dependencies` by nesting depth (PR #39). It then writes the file once at the end. `InitChanges.apply` calls it, and any `SentinelException` thrown there reaches `InitService`, whose `finally` block rolls back every init change. Nothing checks the edited string before the write. Managed-plugin version updates still use a regex (`updateVersion`) that can match across plugin boundaries.

Constraints:
- The project's own `pom.xml` belongs to the user. Edits are text splices, not DOM round-trips, so the rest of the file stays byte-for-byte unchanged.
- Native image: no runtime class generation. Reflective or service-loaded types must be registered in `reachability-metadata.json`.
- Init only detects whether tools are available; it doesn't run gates (the "Quality-gate availability reporting" requirement).

## Goals / Non-Goals

**Goals:**
- Stop a malformed `pom.xml`, or a declaration in the wrong place, before it is written, using a check that runs in-process and offline.
- Make it impossible for a new or edited snippet to pass the test suite unless Maven's own model reader accepts it under every preset.

**Non-Goals:**
- Full validation of the user's existing pom against the POM XSD or Maven's semantics. Init checks only what it added, plus well-formedness of the whole document.
- Changing how existing declarations are detected (`containsArtifact`), or covering Gradle.

## Decisions

### 1. Validate in-process before writing, and don't run `./mvnw validate`
`PomToolConfigurator.apply` calls a new `PomDeclarationValidator` on the final edited string, just before `Files.writeString`. If the check fails, it throws `SentinelException` naming the artifact, and the existing rollback path handles the rest. The file has not been written yet, so restoring `pom.xml` is a no-op.

*Alternatives:*
- **Run `./mvnw -q validate` after writing.** It would also catch problems in the user's own declarations, but it's slow, may need the network to resolve a parent POM, can't tell a network failure from a bad pom, and goes against the rule that init doesn't run builds.
- **Validate each snippet only, not the whole document.** Cheaper, but it would miss bad splices like the profile-placement bug.

### 2. Well-formedness via the JDK SAX parser, configured for untrusted input
Use `SAXParserFactory` with `disallow-doctype-decl` set to true, external general and parameter entities turned off, `XMLConstants.FEATURE_SECURE_PROCESSING` on, and namespace awareness off. The handler does nothing and only reports parse errors. Error messages include the line and column, never file content (AGENTS.md logging rules).

*Alternatives:*
- **Reuse `PomElements` as the well-formedness check.** It's deliberately lenient: it doesn't check entity syntax or that tags are properly balanced, and it shouldn't double as a validator.
- **Use DOM.** It builds a tree we don't need and makes it easier to leave XXE protection off by accident.

Because DOCTYPE declarations are rejected, a user pom with a DOCTYPE fails validation. Maven itself doesn't accept POMs with a DOCTYPE in practice, so this is accepted (see Risks).

### 3. Check structure only for what init added
For each tool that `apply` added during this run, the validator locates the declaration with `PomElements`: in `project/build/plugins` for plugins, in `project/dependencies` for dependencies. It finds the child whose `<artifactId>` text matches, then checks that the element's direct children are in the set Maven allows:
- **plugin:** `groupId`, `artifactId`, `version`, `extensions`, `executions`, `dependencies`, `goals`, `inherited`, `configuration`
- **dependency:** `groupId`, `artifactId`, `version`, `type`, `classifier`, `scope`, `systemPath`, `exclusions`, `optional`

Both sets come from the Maven 4.0.0 POM model. They are constants with a comment pointing to the model. Content inside `<configuration>` is plugin-specific and isn't checked.

`PomElements` gains a `children(xml, parent)` method that lists the tags directly under an element, plus a method that returns an element's text. Both reuse the existing tag scanner.

*Alternative:* bundle `maven-model` at runtime and use its strict reader. That adds a dependency to the runtime and the native image, plus reflection-free but sizeable parsing code, just to check about 20 element names. Rejected; the strict reader is used in tests instead (Decision 5).

### 4. Scope version updates with `PomElements`
Replace the regex in `updateVersion`:
1. Find the plugin in `project/build/plugins` whose `artifactId` matches.
2. Find its direct `<version>` child and confirm its text equals the inspected version.
3. Replace exactly that text range.

If the plugin is missing from the project-level build but `containsArtifact` found it elsewhere (for example, only in a profile), emit the same warning as a declined update and leave the file unchanged. If it's in the project-level build but has no version, or a different one, throw the existing "Could not safely update" `SentinelException`.

### 5. Test matrix with Maven's strict model reader at test scope
Add `org.apache.maven:maven-model` (3.9.x) at `test` scope.

A parameterized test, `PomToolMatrixTest`, iterates three dimensions:
- **gates:** every gate with a Maven tool in the `TOOLS` table, plus all of them together
- **presets:** `null` and every value of `QualityPreset.values()`
- **fixture poms**, under `src/test/resources/fixtures/poms/`:
  - minimal
  - no `<build>`
  - `<build/>`
  - a profile before the project build
  - `pluginManagement` and `dependencyManagement`
  - comments containing closing tags
  - Spring Boot parent with namespace attributes

For each combination, the test:
- runs `PomToolConfigurator.apply` on a temp copy
- parses the result with `MavenXpp3Reader.read(reader, true)` (strict)
- asserts that each reported tool appears in `model.getBuild().getPlugins()` or `model.getDependencies()`
- asserts that no profile's plugins changed

Iterating `QualityPreset.values()` means a future preset is covered automatically. That closes the gap that let the PIT bug through.

The legacy constructor path (`compatibilityEnabled == false`) shares the same snippets and insertion code, so the matrix runs through it, which avoids needing a compatibility inspection stub per tool. Separate focused tests cover the compatibility path for validation and version scoping.

## Risks / Trade-offs

- **[Risk] The JDK XML parser misbehaves in the native image** (its factory lookup goes through ServiceLoader and falls back to the built-in Xerces). → Mitigation: add a native-path assertion. Run `init` with a Maven gate in `NativeInitTerminalSmokeTest`, or a focused native test, and register any missing types in `reachability-metadata.json`. `NativeImageCompatibilityTest` already rejects runtime class generation, which SAX doesn't use.
- **[Risk] A legitimate pom with a DOCTYPE is now rejected by init.** → Mitigation: the error says DOCTYPE declarations aren't supported for automated edits, so the user can add the plugins by hand. Maven POMs essentially never contain one.
- **[Risk] The allowed-children sets drift from Maven's model.** → Mitigation: the strict reader in the matrix test fails if a snippet uses an element Maven rejects, independently of the hand-maintained sets.
- **[Trade-off]** The user's pre-existing declarations aren't validated beyond well-formedness, so a user pom that is already invalid but well-formed still passes. That's acceptable, because init didn't cause it.

## Migration Plan

No data or config migration. This ships as a normal release. The new error affects only init runs that would previously have written a broken `pom.xml`. Rollback is reverting the change; there's no persisted state.
