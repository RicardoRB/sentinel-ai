# Project inspection

Every step here is read-only. Do not create, edit, or delete files while inspecting, and do not run
`ward check` or any build command: inspection must be safe in a repository you have not been told
to trust. Pass the project directory with `-C <dir>` when it is not the current directory.

Collect the findings into a short summary for the user before asking the first question.

## 1. Supported project

```sh
ward detect -C <dir>
```

- Exit `0`: note the reported language, build tool (`MAVEN` or `GRADLE`), framework, and project root.
  Use that root for every later step.
- Exit `1`, or a project that is not Maven or Gradle (for example the `package.json` at a monorepo
  root): look for Maven and Gradle projects below the directory. List `pom.xml`,
  `build.gradle(.kts)` and `settings.gradle(.kts)` files without descending into `node_modules`,
  `target`, `build`, `dist`, `out`, or hidden directories, and treat a directory listed in a parent
  `pom.xml` `<modules>` or below a `settings.gradle(.kts)` as a module of that parent, not a
  project. If none remain, tell the user Ward cannot be initialized here and stop. Otherwise list
  them and ask which to configure; each answer becomes one `--project <dir>` option, and
  `ward detect -C <dir>/<project>` gives that project's details. Agent integrations then install
  at the repository root: Claude Code and OpenCode with one set of files per project, the other
  agents with one hook that every project shares and that checks the project of each edited file.
- Exit `2`: Ward could not run. Show the error and stop.

## 2. Build readiness

```sh
ward doctor -C <dir>
```

Read one line per check:

- `maven` / `gradle`: `OK` means the wrapper or executable is available. `ERROR` means gates that use
  the build tool cannot run until it is installed. Say so, because it affects every build-based gate.
- `configuration`: `ERROR` with `Run 'ward init'` means there is no `ward.toml` yet. `OK`
  means one exists (see step 5).
- `integration`: lists agent integrations already installed, as `id=status`.

Exit `2` from doctor is expected when `ward.toml` is missing. It is not a failure of the
inspection.

## 3. Package layout and architecture style

List the packages under the main source root (`src/main/java`, or `src/main/kotlin` for Kotlin):

```sh
find src/main/java -type d | sed 's|src/main/java/||' | sort
```

If there is no main source directory yet, say that the architecture cannot be inferred and recommend
`layered`, init's default.

Otherwise, find the base package (the deepest directory shared by all sources, for example `com/acme/shop`) and
look at the segments directly below it. Match them against these styles. These are the only values
`--architecture` accepts:

| Style | Typical segments below the base package |
|---|---|
| `hexagonal` | `domain`, `application`, and `infrastructure` or `adapter` (often `port`, `ports`) |
| `clean` | `entities` or `entity`, `usecase` or `usecases`, `interfaceadapters` or `adapters`, `frameworks` |
| `layered` | `controller` or `web`, `service`, `repository` or `persistence`, `model` |

- Recommend the style whose segments clearly dominate, and name the segments that support it.
- If the layout mixes styles, is organized by feature (`order`, `customer`, ...), or is too small to
  tell, say so plainly. Recommend `layered` as init's default and explain that the generated rules can
  be tightened later with `ward-tune`.
- Check a sample of imports before claiming the code already follows the style. For example, list
  `domain` files that import `infrastructure`:

  ```sh
  grep -rl "import .*\.infrastructure\." src/main/java/<base>/domain
  ```

  Any hit means the generated ArchUnit rule will fail on the first check. Tell the user before they
  choose `archunit`.

## 4. Existing ArchUnit tests

```sh
grep -rl "com.tngtech.archunit" src/test --include='*Test.java' --include='*Tests.java'
```

If any classes are found, init reuses them. It does not generate `ArchitectureTest.java`, and it
points the `archunit` gate command at those classes. The dry-run plan shows this only as a missing
`create ... ArchitectureTest.java` line. It does not list the reused classes, so tell the user which
classes you found and that the gate will run them. In that case the architecture style has no effect.
Do not ask the user to choose one, and keep `--architecture` out of the command.

## 5. Existing Ward configuration

If `ward.toml` exists in the project root, read it as text. Reading it is safe. Running its gate
commands is not, so do not run `ward check` here.

- `preset = "<id>"` records the preset it was generated with.
- Each `[quality-gates.<id>]` table with `enabled = true` is a gate in use. `profiles` shows when it
  runs.
- Ask yourself whether the file existed before this session. If it did, treat it as untrusted until
  the user confirms the repository is trusted (see the guardrails in `SKILL.md`).

Reconfiguring needs `--overwrite`. Without it, a non-interactive `ward init` prints
`Kept existing ward.toml. No changes were made.` and changes nothing.

## 6. Rule files and ownership

Look for rule files that Ward presets write:

```sh
head -1 config/checkstyle.xml config/pmd-ruleset.xml config/enforcer-rules.xml config/spotbugs-exclude.xml 2>/dev/null
```

- A first line `<!-- managed-by: ward preset=<id> -->` means the file is Ward-owned. A later
  `ward init` refreshes it, and the plan shows it as `modify`.
- No marker means the file is user-owned. Init keeps it, and the plan lists it as `keep`.

Tell the user which rule files exist and who owns them. Tuned rules in a managed file are lost the
next time init runs. That is why `ward-tune` offers to move ownership to the user.

## 7. Gate tool availability

`doctor` does not report whether each gate's tool is installed. Get that from a dry-run probe that
selects every gate. `--dry-run` writes nothing, installs nothing, and runs no gate:

```sh
ward init -C <dir> --dry-run --preset strict --integration none --architecture layered \
  --gate compile,tests,coverage,mutation,checkstyle,pmd,spotbugs,sonar,format,semgrep,gitleaks,dependency-check,zap,trivy,archunit,enforcer,license,api-compat
```

Add `--overwrite` when `ward.toml` already exists. Without it the probe prints nothing useful.
On a Maven project whose `pom.xml` has no `<distributionManagement>`, drop `api-compat` from the
list: init offers it only for published libraries and rejects it otherwise.
The probe still writes nothing.

Read these lines from the output:

- `Selected quality gate '<id>': AVAILABLE` means the gate can run.
- `Selected quality gate '<id>': UNAVAILABLE - <reason>` means its tool is missing. Keep the reason
  for when the user considers that gate.
- `install    <command>` is the installation init would run if the user consents with
  `--install-tool <id>`.
- `guidance   <tool>: <url>` means the tool can only be installed by hand.
- `Warning: NVD_API_KEY is not set` means `dependency-check` will be slow or rate-limited without a
  key.
- `Warning: openspec/ is ignored by Git` means the OpenSpec gate passes locally but fails in CI.
  Ask whether to track `openspec/` or keep the validation out of the `ci` profile.

Ignore the rest of the probe's plan. It reflects the probe's selection, not the user's.

## 8. Other context

- `.git/hooks/pre-commit` and `.git/hooks/pre-push`: existing hooks. Ward's quality hooks are
  inserted into them without replacing user logic.
- `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`: CI already exists. Check with `grep -l
  'ward check' .github/workflows/*` whether a workflow already runs Ward. A first line
  `# ward-owned: github-ci v1 begin` in `.github/workflows/ward.yml` means init wrote it.
  Init generates only GitHub workflows (`--ci github`); for other CI, the `ci` profile can be run
  from the existing pipeline.
- `AGENTS.md`: a `<!-- ward-owned: agent-rules v1 begin -->` section means init wrote the gate
  rules there before.
- The harness you are running in, for example Claude Code, OpenCode, or Codex. It is the natural
  candidate for `--integration`.

## Summary for the user

Before the first question, show something like this:

```text
Project: Maven, Java, Spring Boot (root: /path/to/project)
Build: Maven Wrapper available
Layout: com.acme.shop -> domain, application, infrastructure (looks hexagonal)
ArchUnit: none found, so init would generate ArchitectureTest.java
Config: no ward.toml yet
Rule files: none
Unavailable tools: semgrep, gitleaks (brew can install them), zap (manual)
CI: GitHub repository, no workflow runs Ward yet
Notes: NVD_API_KEY is not set
```
