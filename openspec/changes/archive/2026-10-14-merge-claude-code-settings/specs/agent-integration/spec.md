# Spec Delta

## MODIFIED Requirements

### Requirement: Upgrade of Sentinel-owned integration files
Re-running `sentinel integrate <agent>` SHALL replace Sentinel-owned integration files whose content differs from the current generated content and report them as changed. User-owned integration files SHALL be preserved and reported as conflicts, except that Claude Code settings SHALL be merged when valid JSON so unrelated user configuration is retained.

#### Scenario: Outdated owned hook
- **WHEN** a project has a Sentinel-owned Claude Code hook that calls `./verify-quality.sh` and the user runs `sentinel integrate claude-code`
- **THEN** the hook is rewritten with the current content and listed as changed

#### Scenario: User-owned file
- **WHEN** an integration target other than Claude Code settings exists without a Sentinel ownership marker
- **THEN** Sentinel leaves it untouched and reports a conflict

#### Scenario: Merge existing Claude Code settings
- **WHEN** `.claude/settings.json` contains valid user-owned settings or hooks and the user runs `sentinel integrate claude-code`
- **THEN** Sentinel adds its hook registrations while preserving all unrelated settings and hook entries

#### Scenario: Malformed Claude Code settings
- **WHEN** `.claude/settings.json` exists but cannot be parsed as valid JSON
- **THEN** Sentinel reports a conflict and leaves the file and other integration targets unchanged

#### Scenario: Remove Sentinel hooks from Claude Code settings
- **WHEN** the user runs `sentinel integrate claude-code --remove` on settings containing Sentinel and user-owned entries
- **THEN** Sentinel removes only its hook registrations and preserves the other settings and hooks

#### Scenario: Preserve settings file when no entries remain
- **WHEN** removal leaves `.claude/settings.json` with no Sentinel entries
- **THEN** Sentinel preserves the settings file, including when it contains no other settings
