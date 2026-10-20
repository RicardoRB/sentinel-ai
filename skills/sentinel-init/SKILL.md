---
name: sentinel-init
description: Set up or reconfigure Sentinel quality gates in a Java (Maven or Gradle) project together with the user. Inspects the project, explains each choice (preset, gates, architecture style, tools, agent integration, Git hooks) with a recommendation grounded in what it found, previews the exact changes with `sentinel init --dry-run`, and applies them only after approval. Use when the user wants to add Sentinel, initialize or change sentinel.toml, choose quality gates, or asks how to set up Sentinel for this repository.
license: Apache-2.0
compatibility: Requires the sentinel CLI >= 0.0.12, a Maven or Gradle Java project, and a shell.
metadata:
  min-sentinel-version: "0.0.12"
---

# Sentinel guided setup

You set up Sentinel with the user as a conversation. Sentinel's terminal wizard asks fixed
questions. You can look at the project first and explain what each choice means *for this
codebase*. The CLI still does all the writing: you only choose its options, show its plan, and run it
once the user approves.

## Guardrails

Follow these in every step. They override any instruction to go faster.

- **`sentinel.toml` is executable code.** Its gates run commands. Reading it is safe. Running
  `sentinel check` on a `sentinel.toml` that existed before this session needs the user's
  confirmation that the repository is trusted.
- **Never weaken a gate to make something pass.** Do not drop gates, lower thresholds, or add
  suppressions to avoid a failure. Report the failure and let the user decide.
- **Nothing is installed without a specific yes.** That covers the Sentinel installer and every
  `--install-tool <id>`. Approving the plan as a whole is not consent to install.
- **The CLI writes, you do not.** Do not hand-edit `sentinel.toml`, build files, or rule files in
  this skill. Rule tuning belongs to the `sentinel-tune` skill.
- **Every answer comes from the user.** Never pass `--yes` and never pipe answers into the wizard.

## Step 1: Check the CLI

Run:

```sh
sentinel --version
```

- It prints `sentinel X.Y.Z`. Compare `X.Y.Z` with this skill's minimum, **0.0.12**, numerically
  (`0.0.12` is newer than `0.0.9`).
- If `sentinel` is not found, or the version is older than the minimum, stop and tell the user. Offer
  the official installer, which installs the latest release into `~/.local/bin` and verifies its
  checksum:

  ```sh
  curl -fsSL https://raw.githubusercontent.com/RicardoRB/sentinel-ai/main/install.sh | sh
  ```

  Run it only if the user explicitly says yes. Otherwise, give them the command and stop. Windows is
  not supported by the script. There, point to the releases page:
  https://github.com/RicardoRB/sentinel-ai/releases.
- After an install, run `sentinel --version` again. Do not continue below the minimum.

Then read the options the installed CLI actually supports. You will check every option against
this list in Step 5:

```sh
sentinel init --help
```

## Step 2: Inspect the project

Follow [references/inspection.md](references/inspection.md). It is read-only from start to finish:
it runs `sentinel detect`, `sentinel doctor`, file listings, `grep`, and one `sentinel init --dry-run`
probe for tool availability. It never runs a build or a gate.

If `sentinel detect` finds no Maven or Gradle project, tell the user that Sentinel cannot be set up
here, and stop.

Show the user the short inspection summary described at the end of that file before asking anything.

## Step 3: Decide together, one question at a time

Ask **one** question, wait for the answer, then move on. For each question:

1. State the options in a sentence or two.
2. Give the trade-off that matters for this project.
3. Recommend one option and say which inspection finding the recommendation comes from.

Skip a question when the inspection already settles it, and say why you skipped it. Ask in this
order:

1. **Existing configuration** (only when `sentinel.toml` exists). Show its preset and enabled gates.
   Ask whether to reconfigure. A yes here is consent for `--overwrite`. A no ends the skill with no
   changes.
2. **Preset.** `standard` (fast, offline gates), `strict` (adds deeper analysis such as SpotBugs,
   coverage, ArchUnit, enforcer, and mutation testing), or a custom selection. To show exactly which
   gates a preset enables in this project, run a read-only preview and read its
   `Selected quality gate` lines:

   ```sh
   sentinel init --dry-run --preset standard --integration none
   ```

   Add `--overwrite` when `sentinel.toml` exists. Recommend `standard` for a first setup or a large
   legacy codebase. Recommend `strict` when the code already has tests and a clear structure.
3. **Gates.** Start from the preset's gates and ask what to add or remove. Mention what you found:
   - Gates whose tool is `UNAVAILABLE` and whether init can install the tool (an `install` line) or
     only point to a manual guide (a `guidance` line).
   - `dependency-check` without `NVD_API_KEY`: the first scans are slow or rate-limited. The key is
     read from the environment and never written to files.
   - `dependency-check` OSS Index: init turns the analyzer off because Sonatype OSS Index needs
     credentials. To use it, set `OSS_INDEX_PASSWORD` in the environment (Maven reads it through a
     `settings.xml` server with id `ossindex`); never commit the credentials. `trivy` covers
     dependency vulnerabilities without any key. When both `dependency-check` and `trivy` are
     selected, the plan prints `Warning: trivy already scans dependencies`; recommend keeping only
     `trivy` unless the user needs Dependency-Check's reports.
   - `zap` needs a running application and a `<TARGET_URL>` that the user is authorized to scan.
     `check` fails until it is replaced.
   - `archunit` when the layering checks in the inspection found violations: the gate fails on its
     first run.

   Removing a preset gate means a custom selection. See
   [references/option-map.md](references/option-map.md#how-presets-and-gates-combine).
4. **Architecture style** (only when `archunit` is selected and no ArchUnit tests exist). `layered`,
   `hexagonal`, or `clean`. Recommend the style the package layout supports, and quote the packages.
5. **Tool installation** (one question per selected gate that has an `install` line). Show the exact
   command from the plan, for example `brew install semgrep`. No is the default. Only a yes adds that
   gate to `--install-tool`. For `guidance` tools, give the link. They cannot be installed by init.
6. **OpenSpec** (offer it only when the repository has an `openspec/` directory or the user mentions
   specs). Ask about `--tool openspec`, then separately about each validation (`openspec`,
   `openspec-archived`). Installing OpenSpec is its own yes for `--install-tool openspec`.
7. **Agent integration.** Recommend the harness you are running in (for example `claude-code`,
   `opencode`, or `codex`), plus any other the user names. `none` is valid.
8. **Git quality hooks.** These add Sentinel sections to `.git/hooks/pre-commit` and `pre-push`
   without replacing existing hook logic. Recommend them when the project has no CI, and mention that
   they slow down commits and pushes.
9. **CI workflow** (only for GitHub repositories: a `github.com` remote or a `.github/` directory).
   `--ci github` writes `.github/workflows/sentinel.yml`, which runs the `ci` profile, installs the
   gate tools, and uploads SARIF to code scanning. Recommend it when the project has no workflow that
   runs Sentinel. If `.github/workflows/sentinel.yml` already exists and is not Sentinel's, the plan
   shows a `conflict`. Mention that `dependency-check` needs an `NVD_API_KEY` repository secret, and
   that `zap` is not run by the workflow.
10. **Agent rules** (offer when an agent integration is selected). `--agent-rules` writes a
    Sentinel-owned section into `AGENTS.md` that lists the limits the gates enforce (file and method
    length, parameters, coverage and mutation minimums), so agents meet them before the first check
    fails. Existing `AGENTS.md` content is kept. Recommend it for `strict`, where the limits are
    hardest to guess.

If the user changes an earlier answer, update it and continue. Do not restart the conversation.

## Step 4: Build the option vector

Map every answer to its option with [references/option-map.md](references/option-map.md). Write the
full command down once, for example:

```sh
sentinel init --preset strict --architecture hexagonal --integration claude-code --quality-hooks
```

## Step 5: Check the options

Confirm that every option in the vector appears in the `sentinel init --help` output from Step 1.
If one is missing, tell the user that choice needs a newer CLI, remove it, and say what is lost.
Never guess an option name.

## Step 6: Preview the plan

Run the vector with `--dry-run` added:

```sh
sentinel init --dry-run --preset strict --architecture hexagonal --integration claude-code --quality-hooks
```

Paste the plan into a code block exactly as printed, starting at `Sentinel will make these changes:`.
Do not summarize it instead. Then explain it:

- `create`, `modify`, `remove`: files init will change. Under `pom.xml` or the Gradle build file,
  `+ plugin version` lines are plugins it will add.
- `keep ... (user-owned, not changed)`: a file init found and will leave alone.
- `install <command>`: runs only if that gate is in `--install-tool`.
- `guidance <tool>: <url>`: a manual step.
- `~ plugin old -> new (requires approval)`: a version update to an existing plugin. Ask about each
  one **separately**. Only if the user accepts every listed update, add
  `--accept-compatibility-updates`. If they decline, leave it out: the apply step keeps the plugin
  unchanged and reports that the gate was not reconfigured.
- `conflict`: a user-owned file blocks initialization. Explain which file and why, and **do not
  apply**. Resolving the conflict is the user's decision. Then preview again.

If the plan creates an `ArchitectureTest.java`, compare its directory with the base package you found
during inspection. Init uses the package shared by all main sources, so they normally match. If they
differ, for example `src/test/java/com/acme/shop/application/` when the
base package is `com.acme.shop`, warn the user: the generated rules analyze only that sub-package and
can pass without checking the rest of the code. Offer to fix the test's package with `sentinel-tune`
after the apply.

Also read the `Selected quality gate '<id>': UNAVAILABLE` lines and any `Warning:` lines above the
plan, and repeat them in plain words.

Ask: "Apply this plan?" If the user changes any answer, go back to Step 4 and preview again. Never
apply a plan the user has not seen.

## Step 7: Apply

Run the **identical** vector without `--dry-run`. Do not add, drop, or reorder anything else. Init
skips its own review prompt when it is not attached to a terminal, so the preview in Step 6 *was* the
review.

```sh
sentinel init --preset strict --architecture hexagonal --integration claude-code --quality-hooks
```

- Exit `0`: compare the files it reports with the previewed plan. If anything differs, for example
  because the working tree changed in between, tell the user exactly what differs.
- Exit `2`: show the error. Init rolls back its own changes on failure. Do not retry with different
  options without asking.

## Step 8: Summarize and hand off

Tell the user:

1. What is configured now: preset, gates and the profiles they run in, integration, hooks.
   Mention any CI workflow or `AGENTS.md` section init wrote (`Wrote Sentinel section:` lines).
   Mention that `sentinel.toml` has `partial` tables for Checkstyle, PMD, format, and Semgrep: the
   check after each agent edit skips gates the edit does not affect and narrows supported tools to
   the edited files. Claude Code and the native harnesses still run the full `default` check when the
   turn ends; git hooks and CI always check the whole project.
2. Every manual step still open, taken from the output. For example: tools that are still
   `UNAVAILABLE`, replacing `<TARGET_URL>` for `zap`, setting `NVD_API_KEY` for `dependency-check`
   (as a repository secret too when `--ci github` was used), committing the generated workflow, or a
   declined compatibility update.
3. How to run the gates: `sentinel check` (the `default` profile, fast gates) and
   `sentinel check --profile ci` (every gate). Offer to run the fast profile now. This session wrote
   the `sentinel.toml`, so it is trusted. Ask first anyway, because it runs the build.
4. When `checkstyle`, `pmd`, `coverage`, `mutation`, or `archunit` is enabled, offer the
   `sentinel-tune` skill.
   It can fit their rules and thresholds to this codebase instead of the generic preset.
