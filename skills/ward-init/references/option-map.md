# Decision to option map

Each decision the user makes becomes exactly one explicit `ward init` option. Before you use an
option, confirm it appears in `ward init --help` for the installed CLI. If it does not, tell the
user the choice needs a newer CLI and leave it out.

The "Consent" column marks options that need a specific yes for that exact effect. Approving the plan
as a whole is not that yes.

| Decision | Option | Values | Consent |
|---|---|---|---|
| Project directory | `-C <dir>` | Path to the project root | No |
| Preset | `--preset <id>` | `standard` or `strict`. Leave the option out for a custom selection | No |
| Gates | `--gate <id>[,<id>...]` | Gate ids from the gate reference | No |
| Architecture style | `--architecture <style>` | `layered`, `hexagonal`, or `clean`. Only when `archunit` is selected and no ArchUnit tests exist | No |
| Optional tools | `--tool openspec` | `openspec` | No |
| OpenSpec validations | `--openspec-validation <id>[,<id>...]` | `openspec`, `openspec-archived`. Only together with `--tool openspec` | No |
| Agent integrations | `--integration <id>[,<id>...]` | `none`, or any of `claude-code`, `opencode`, `codex`, `cursor`, `copilot`, `gemini`, `windsurf`, `cline`, `factory-droid`, `amp` | No |
| Git quality hooks | `--quality-hooks` | Present to install; leave it out to skip | No |
| CI workflow | `--ci github` | `github`. Leave it out to skip | No |
| Agent rules in `AGENTS.md` | `--agent-rules` | Present to write the section; leave it out to skip | No |
| Remove quality hooks | `--remove-quality-hooks` | Present to remove Ward's hook sections | No |
| Install a missing tool | `--install-tool <id>[,<id>...]` | `openspec`, or a selected gate id that the plan shows with an `install` line | **Yes, per tool** |
| Replace an existing `ward.toml` | `--overwrite` | Present | **Yes** |
| Accept plugin version updates | `--accept-compatibility-updates` | Present | **Yes, after showing each `(requires approval)` line** |
| Preview only | `--dry-run` | Present for the review step; removed for the apply step | No |

## How presets and gates combine

- `--preset standard` or `--preset strict` selects that preset's gates. Adding `--gate` only **adds**
  gates to the preset's set. It never removes a preset gate.
- To drop a preset gate, use a custom selection: leave out `--preset` and list every gate with
  `--gate`.
- Every run needs at least one of `--preset` or `--gate`. Without either, a non-interactive init exits
  with code `2`.

## Options this skill never uses

- `--yes`: it fills unanswered questions with defaults and approves compatibility updates. Every
  answer must come from the user, so this skill never passes it.
- Piped answers, such as `printf '1\n' | ward init`: the order of questions can change between
  versions. Use explicit options only.

## Examples

Strict preset, hexagonal ArchUnit test, Claude Code integration, Git hooks:

```sh
ward init --dry-run --preset strict --architecture hexagonal --integration claude-code --quality-hooks
```

Custom selection without `pmd`, installing Semgrep after the user said yes to that install:

```sh
ward init --dry-run --gate compile,tests,format,checkstyle,coverage,semgrep --install-tool semgrep --integration none
```

Reconfiguring an existing setup after the user agreed to replace `ward.toml`:

```sh
ward init --dry-run --overwrite --preset standard --integration opencode
```
