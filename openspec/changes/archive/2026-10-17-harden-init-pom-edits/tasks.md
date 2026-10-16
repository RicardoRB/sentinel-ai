# Tasks

## 1. Prerequisite

- [x] 1.1 Start from a branch containing PR #39 (`PomElements`, `PomXml`, and the corrected PIT snippet). Verify that `PomXmlTest` passes with `./mvnw -Dtest=PomXmlTest test`.

## 2. Snippet and placement test matrix

- [x] 2.1 Add `org.apache.maven:maven-model` (3.9.x, `test` scope) to the project `pom.xml`. Verify that `./mvnw -q test-compile` resolves it and that `mvn dependency:tree -Dscope=runtime` doesn't list it.
- [x] 2.2 Add fixture poms under `src/test/resources/fixtures/poms/`: minimal, no `<build>`, `<build/>`, profile before build, `pluginManagement` + `dependencyManagement`, comments containing closing tags, and a Spring Boot parent with namespace attributes. Verify that each one parses with `MavenXpp3Reader` in strict mode.
- [x] 2.3 Add `PomToolMatrixTest` covering every Maven tool gate (individually and all together) × (`null` + `QualityPreset.values()`) × each fixture. Each result must parse in strict mode, each reported tool must be in `build.plugins` or `dependencies`, and profile plugins must be unchanged. Verify it passes, and that reintroducing the old PIT snippet (threshold outside `<configuration>`) fails it.

## 3. Validate the edited pom before writing

- [x] 3.1 Add `PomElements.children` and element-text lookup. Verify with `PomElementsTest` cases for nested, self-closing, and comment-adjacent children.
- [x] 3.2 Implement `PomDeclarationValidator`: a SAX well-formedness check with DOCTYPE disallowed, external entities off, and secure processing on, plus placement and allowed-children checks for the added artifacts. Verify with unit tests for: valid edit, disallowed plugin child, disallowed dependency child, malformed XML, a DOCTYPE/XXE payload (rejected without fetching anything), and an added declaration missing from the project-level section.
- [x] 3.3 Call the validator in both `PomToolConfigurator.apply` paths before `Files.writeString`, throwing `SentinelException` that names the tool. Add JUL diagnostics (`event=pom-validation status=passed|failed tool=<artifactId>`) with no pom content. Verify with a configurator test showing `pom.xml` is byte-identical after a forced failure and the exception names the tool.
- [x] 3.4 Add an `InitService`-level test where pom validation fails after integrations were installed. Verify that `sentinel.toml` isn't created and that integration files and `pom.xml` are restored.

## 4. Scope managed-plugin version updates

- [x] 4.1 Replace the regex in `updateVersion` with a `PomElements` lookup of the project-level plugin's direct `<version>`. Verify with `PomToolConfiguratorCompatibilityTest` cases: another plugin shares the old version (only the target changes); the target has no version (`SentinelException`, file unchanged); the target is declared only in a profile (warning, file unchanged).

## 5. Native image and documentation

- [x] 5.1 Exercise pom validation in the native executable by running `init` with a Maven gate in `NativeInitTerminalSmokeTest` or a focused native test. Register any XML types it reports as missing in `reachability-metadata.json`. Verify with `./mvnw -Pnative -DskipTests native:compile` followed by `./mvnw -Dtest=NativeInitTerminalSmokeTest -Dsentinel.native.binary=target/sentinel test`.
- [x] 5.2 Update `website/src/content/docs/commands/init.mdx`:
  - plugins and dependencies go only into the project-level build and dependencies
  - the edited pom is validated before writing, and a failure leaves files unchanged with rollback
  - DOCTYPE declarations aren't supported for automated edits

  Verify that the docs site builds.

## 6. Integration verification

- [x] 6.1 Run `sentinel init` with the strict preset on a copy of this repository and on `src/test/resources/fixtures/maven-project`, then run `./mvnw -q validate` on each result. Verify that both succeed and that `git diff` shows only the added declarations.
- [x] 6.2 Run `./verify-quality.sh`. Verify that it passes with ≥80% JaCoCo instruction coverage and no new suppressions.

## Workflow follow-up

- Open follow-up changes for the proposal's non-goals: checking gate tools are installed at init (such as `gitleaks`), rejecting unfilled placeholders such as `<TARGET_URL>`, and a CI job that runs init on real projects.
- Archive the change after review and merge.
