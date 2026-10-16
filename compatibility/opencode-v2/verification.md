# Verification record

## 2026-10-16: deterministic suite against @opencode/plugin 2.0.30

- Both generated plugins failed to load on 2.0.29 and 2.0.30:
  `SyntaxError: Export named 'define' not found`. The package exports `Plugin.define`. The earlier
  record below used `bun build` with the package external, which does not resolve named exports,
  so it could not detect this.
- Other defects found and fixed: idle events were read from `properties.sessionID` (the runtime
  sends `data.sessionID`); session locations were read from `data.directory` (the runtime returns
  `location.directory`) with a guessed fallback; `session.prompt` used `prompt.parts` and
  `delivery: "followup"` (the API takes `text` and `"steer"`/`"queue"`); post-edit feedback was
  thrown from `execute.after`, which the runtime treats as a defect, and written to `output`
  instead of the content the model reads.
- `bun run test`: 31 tests passing, stable over six consecutive runs.
- Live (`bun run smoke`, OpenCode 2.0.30, no credentials): `plugin-activation` passed. The runtime
  logged `loading plugin` for both files and no load failure. The same probe with plugins generated
  by Sentinel 0.0.7 logged `failed to load plugin ... Export named 'define' not found` for both.
  Model-backed scenarios were blocked on missing credentials, and the idle-limit and reload
  scenarios have no driver. Report: `../evidence/2026-10-16-opencode-live.json`.

## 2026-10-15: superseded

Recorded on Bun 1.3.11 and `@opencode/plugin` 2.0.29. The generated artifacts were only parsed
with `bun build` using the package as an external module, which is not evidence that they load.
No live smoke was run.
