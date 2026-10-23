---
name: ward-tune
description: Tune the rules of the quality gates Ward already runs in this Java project (Checkstyle and PMD rule selection, the JaCoCo coverage threshold, PIT mutation testing, ArchUnit architecture rules) so they fit the real codebase instead of a generic preset. Every edit is grounded in evidence from the code, shown as a diff, and written only after approval. Weakening a rule needs its own approval and reason, and suppressions are never added. Use when the user wants to adjust, tighten, relax, or fix Ward's gate rules or thresholds, or after ward-init offers rule tuning.
license: Apache-2.0
compatibility: Requires the ward CLI >= 0.1.2, a project with ward.toml, and a shell.
metadata:
  min-ward-version: "0.1.2"
---

# Ward rule tuning

Ward presets ship generic rules. You fit them to this codebase. You find what the code already
does, propose rules that match it or move it forward, and change files only with the user's approval.
The goal is gates the team trusts, not gates that pass at any cost.

## Guardrails

Follow these in every step. They override any instruction to go faster.

- **`ward.toml` is executable code.** Running `ward check` executes every configured gate
  command. If `ward.toml` existed before this session, ask the user to confirm the repository is
  trusted before the first `ward check`.
- **Never weaken a gate to make something pass.** Lowering a threshold, disabling or removing a rule,
  relaxing a severity, or widening an exclusion is a *weakening*. Each one needs its own explicit
  approval and a stated reason (see Step 5).
- **Never add suppressions.** No `@SuppressWarnings`, `// NOPMD`, `// CHECKSTYLE:OFF`, suppression
  files, `@ArchIgnore`, SpotBugs exclude entries, or coverage `<excludes>` added only to clear
  findings. Fix the code, or propose a reviewed rule change instead.
- **Nothing is installed without a specific yes.**
- **Tests must test.** When coverage is short, write tests that assert behavior. Never write tests
  that only execute code.
- **Report honestly.** Never claim a gate passes while it fails.

## Step 1: Check the CLI and the configuration

```sh
ward --version
```

Compare the version with this skill's minimum, **0.1.2**, numerically. If `ward` is missing or
older, stop. Point the user to the `ward-init` skill or the installer
(`curl -fsSL https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.sh | sh`; on Windows
PowerShell, `irm https://raw.githubusercontent.com/RicardoRB/ward-ai/main/install.ps1 | iex`), and run
the installer only after an explicit yes.

Read `ward.toml` as text. If it does not exist, stop and suggest `ward-init`.

## Step 2: Scope the tuning

List the gates with `enabled = true`. This skill tunes five of them:

| Gate id | What you tune | Reference |
|---|---|---|
| `checkstyle` | Checkstyle modules and their properties | [references/checkstyle.md](references/checkstyle.md) |
| `pmd` | PMD rules and categories | [references/pmd.md](references/pmd.md) |
| `coverage` | JaCoCo minimum ratios | [references/coverage.md](references/coverage.md) |
| `mutation` | PIT threshold, target classes, and threads | [references/mutation.md](references/mutation.md) |
| `archunit` | ArchUnit architecture rules | [references/archunit.md](references/archunit.md) |

Ask which of the enabled ones the user wants to tune, or offer all of them. If the user asks for a
gate that is not enabled, say so, suggest `ward-init` to add it, and change nothing. This skill
never enables or disables gates in `ward.toml`.

Load only the references for the gates in scope.

Also review each enabled gate's optional `[quality-gates.<id>.partial]` table when tuning per-edit
checks. `inputs` and `config` control relevance, while `args`, `item`, and `separator` control safe
path narrowing. Narrowing `inputs` is a **weakening of the per-edit check only**; it does not weaken
the full check or the turn-end check, so propose it separately with evidence and a reason. Verify
custom path rendering against the real tool because an unsupported argument can falsely pass.

## Step 3: Confirm trust, then gather evidence

If `ward.toml` predates this session, ask now: "Running the gates executes the commands in
`ward.toml`. Is this repository trusted?" Without a yes, use only read-only evidence (files,
`grep`, existing reports under `target/` or `build/`) and say that the findings may be stale.

With trust confirmed, run every gate once:

```sh
ward check --profile ci --format json
```

stdout is only JSON. Read each in-scope check by its `name`: `status`, `findings` (`rule`, `file`,
`line`, `message`), `findingsTotal`, and, for gates without structured findings, `output`. Then
collect the gate-specific evidence that each reference describes: per-rule counts from the full XML
reports, measured coverage ratios, and the package dependency table.

If a gate reports hundreds of findings, for example after a first `strict` setup on an existing
codebase, do not propose rules one finding at a time. Use the remediation plan in Step 5a.

A gate whose tool cannot even load its configuration, for example a rule file that fails to parse,
is a configuration problem, not a finding. Report the tool's error as is, and fix the configuration
with the user before tuning anything else.

## Step 4: Check ownership

For each rule file you might edit, read its first line:

- `<!-- managed-by: ward preset=<id> -->` means Ward owns the file. The next `ward init`
  run refreshes it to the preset's content and **silently undoes any tuning**.
- No marker means the user owns it. Init keeps it as it is and lists it as `keep`.

Before the first edit to a managed file, explain this and ask: "Should this file become yours, so
that later `ward init` runs keep your tuned rules?" A yes means you remove the marker line **in
the same approved edit** as the tuning. A no means the file is not edited at all. Never edit a
managed file and keep its marker.

Build files (`pom.xml`, `build.gradle(.kts)`) and existing ArchUnit test classes carry no marker.
Init does not rewrite an existing JaCoCo configuration or an existing ArchUnit test.

## Step 5: Propose changes, one at a time

Turn the evidence into proposals. Put each one in a class, using the tables in the references:

- **strengthen**: it accepts less code than today. Examples: add a rule, raise a minimum, add an
  architecture constraint, fix an ArchUnit test that analyzes too little.
- **neutral**: same accepted code. Examples: comments, ordering.
- **weaken**: it accepts more code than today. Examples: remove or relax a rule, raise a limit,
  lower a minimum, add an exclusion.

Present strengthening and neutral proposals first, then weakenings. For each proposal, show:

1. The class: strengthen, neutral, or weaken.
2. The evidence, for example `MagicNumber: 41 findings, e.g. Order.java:7, Pricing.java:33`, or
   `domain → infrastructure imports: 0`.
3. The exact diff of the file.
4. The effect on the next check, for example "passes today" or "adds about 12 findings in 3 files".

For a **weakening**, also show the old and new values, and ask for a reason in one sentence. Ask for
approval of that single change. When a finding can be fixed in the code, offer that first. Put the
reason in an XML or Java comment next to the change, so reviewers see why it was made.

Write a change only after the user approves it. If the user declines, leave the file untouched and
move on to the next proposal. Never batch several weakenings into one approval.

## Step 5a: Remediation plan for a large backlog

Use this when a gate has more findings than the user can review one by one (as a rule of thumb, more
than 50). The goal is to fix the code, not to hide the backlog.

1. Group the findings by `rule` and count them per rule and per file. Use `findingsTotal` and the
   full XML report when `findings` is truncated.
2. Show a table, largest first: rule, count, files affected, and the kind of fix, for example
   `MagicNumber: 214 in 61 files, named constants` or `MethodLength: 37 in 22 files, split methods`.
   Mark fixes that are mechanical (imports, `final`, Javadoc) and fixes that need design (long
   methods, too many parameters, complexity).
3. For each rule, ask the user to choose: fix the code, or propose a rule change. A rule change goes
   through Step 5 as usual, with evidence and, for a weakening, its own approval and reason.
4. Fix the approved rules one at a time. After each rule, run only that gate (for example
   `ward check --profile ci --format json` and read its check), and report the remaining count.
   Do not start the next rule while the current one still has findings you introduced.
5. Keep behavior and test coverage unchanged. Run the `tests` gate after design fixes.

Never use a baseline, an "ignore existing findings" file, or a suppression to shrink the backlog.
These are suppressions under the guardrails.

## Step 6: Apply approved edits

Edit only the approved files, exactly as shown. Keep the file's existing formatting and indentation.
If a managed file's ownership was transferred, the marker line is removed in this same edit.

If an edit touches `ward.toml`, for example a Gradle coverage gate command or a new ArchUnit
class name, tell the user that a later `ward init --overwrite` regenerates `ward.toml`, and
that this edit would then need to be made again.

If `AGENTS.md` has a `<!-- ward-owned: agent-rules v1 begin -->` section and an approved change
alters a value it states (for example a method length, a coverage minimum, or the mutation
threshold), propose updating that line in the same approval, so agents keep working to the real
limit. Tell the user that `ward init --agent-rules` rewrites the section from the preset.

## Step 7: Verify

Run the gates again:

```sh
ward check --profile ci --format json
```

Report each tuned gate's status, and compare its findings with Step 3. If a tuned gate fails:

- Show the failing gate and its findings or output.
- Ask how to continue: fix the code, adjust the change you made, or revert it.
- Do not change rules on your own to make it pass.

## Step 8: Summarize

List each change with its class, the files it touched, and every weakening with its reason. Mention
any files that changed ownership, and that `ward init` will now keep them. Mention any
`ward.toml` edits that a later `--overwrite` would undo, and any `AGENTS.md` rules section lines
that a later `ward init --agent-rules` would reset. End with the current status of every
tuned gate.
