# Sentinel JSON result schema

`sentinel check --format json` emits schema version `1` on stdout. Diagnostics are written to
stderr. Consumers must inspect `schemaVersion` and may ignore fields added in later compatible
versions; existing fields retain their meaning within a schema version.

```json
{
  "schemaVersion": 1,
  "status": "PASSED|FAILED|ERROR",
  "project": { "root": "string", "language": "string", "buildTool": "string", "framework": "string" },
  "checks": [{
    "name": "string", "status": "PASSED|FAILED|SKIPPED|UNAVAILABLE|EXECUTION_ERROR",
    "command": "string", "exitCode": "number", "durationMs": "number",
    "stdout": "string", "stderr": "string", "summary": "string", "output": "string"
  }]
}
```

Gate commands are direct argument vectors, not shell expressions. `sentinel.toml` is trusted code
execution and must be reviewed like a build script.
