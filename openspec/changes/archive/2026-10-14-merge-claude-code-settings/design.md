# Design

## Context

See proposal.md for the motivation and scope. `ClaudeCodeIntegration` currently writes a complete generated settings document with a top-level Sentinel marker. Its preflight requires that marker before touching an existing settings file; this prevents collisions but also prevents coexistence with ordinary Claude Code settings. The project already provides a `JsonCodec` backed by Fory and uses ownership markers and full preflight before updating integration files.

## Goals / Non-Goals

**Goals:**
- Treat Claude settings as a shared JSON document rather than an exclusively owned artifact.
- Preserve unrelated settings and hook registrations during install, refresh, and removal.
- Ensure malformed JSON or an unresolvable ownership conflict causes no partial changes.
- Keep OpenCode's separate-file ownership and conflict behavior unchanged.

**Non-Goals:**
- Preserve JSON whitespace or key ordering byte-for-byte; semantic configuration is the preservation contract.
- Merge arbitrary user content into Sentinel's standalone hook scripts.
- Change hook behavior, CLI syntax, or the optional guard feature.

## Decisions

### Parse, validate, and merge settings before writing any integration artifacts

Use the existing JSON codec to parse an existing `.claude/settings.json` into a mutable JSON object representation, reject malformed or non-object roots, and construct the complete updated document before writing it. Keep the existing full conflict preflight for standalone hook scripts and the guard script. Do not write any target until settings parsing, ownership checks, and all target preflights have succeeded.

This avoids introducing a JSON dependency and avoids the unsafe alternative of textual insertion into JSON. On serialization, formatting may change, but user settings and hook values remain semantically intact.

### Identify Sentinel hook registrations by their generated commands

Add only Sentinel's hook entries to the matching Claude hook event arrays (`PostToolUse`, `Stop`, and the optional `PreToolUse` guard). Make installation idempotent by detecting the Sentinel command in each entry and avoid duplicating it. On removal or guard disable, remove only entries that reference Sentinel's dedicated hook command paths; preserve other matchers, hooks, and settings. Remove Sentinel's ownership marker only when it identifies Sentinel's settings integration, and keep `.claude/settings.json` even if removing Sentinel leaves it empty.

An existing non-Sentinel entry at a Sentinel-specific command path is ambiguous ownership; return a conflict instead of replacing or deleting it. Retain the existing marker-based refresh behavior for Sentinel-owned standalone scripts.

### Keep update/removal transactional across integration targets

Preflight every affected artifact and compute the resulting settings content before changing files. If settings are invalid or any standalone target conflicts, leave all settings and artifacts unchanged. For successful removal, write the settings document with only Sentinel entries removed, then delete only standalone artifacts carrying Sentinel ownership markers.

This favors safety and predictable rollback over attempting best-effort partial integration. OpenCode remains untouched because its integration does not register through a shared settings file.

## Risks / Trade-offs

- [JSON serialization changes whitespace or key ordering] -> Preserve parsed values and document semantic rather than byte-level preservation; tests should assert unrelated nested values survive.
- [A user independently creates an entry that uses a Sentinel command path] -> Treat the ambiguous entry as a conflict and do not remove or overwrite it.
- [Unexpected Claude settings shapes or non-array hook values] -> Reject the incompatible structure before writes with an actionable conflict; do not silently replace user data.

## Migration Plan

No project migration is required. Existing Sentinel-owned settings can be parsed and refreshed into the merged representation; users with unmarked settings gain additive installation. Removal leaves the settings file in place and deletes only Sentinel-owned registrations and standalone artifacts. Rollback is to run the previous release's integration removal only for integrations it can identify safely; do not restore a prior whole-file snapshot over user edits.
