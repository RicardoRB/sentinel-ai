# OpenCode V2 compatibility tests

This directory is an opt-in compatibility check for the generated files in
`.opencode/plugins`. It is deliberately separate from the Java test suite: the
ordinary `./mvnw -q test` command does not need Bun, OpenCode, network access,
or model credentials.

## Deterministic generated-plugin suite

From the repository root:

```sh
cd compatibility/opencode-v2
bun install --frozen-lockfile
bun test opencode-v2.test.ts
```

The suite must load the generated artifacts, not a copied template. Run
`bun test` only after a fixture project has been integrated with
`sentinel integrate opencode`; a missing Bun, package, or generated artifact is
an explicit non-success result.

## Isolated live smoke test

```sh
bun run smoke.ts --fixture /absolute/path/to/trusted-fixture
```

The smoke test requires a real OpenCode V2 executable and the pinned package.
It exits `2` and records the missing prerequisite when either is unavailable;
that result is not compatibility evidence. Never run it against a user project.
