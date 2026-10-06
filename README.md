# Sentinel

> Sentinel is a quality gate orchestrator for AI coding agents.

Configure project checks in `sentinel.toml`, run them with one command, and get clear results for
people or automation. Sentinel currently detects Java/Maven projects and provides project-local
integrations for Claude Code and OpenCode.

## Documentation

The full documentation—including installation, commands, configuration, gate references,
integrations, security, and architecture—is available at the
[Sentinel documentation site](https://sentinel-ai.github.io/sentinel-ai/).

## Development

Requires JDK 25; Maven is provided by the wrapper.

```bash
./mvnw package
./verify-quality.sh
```

See [AGENTS.md](AGENTS.md) for architecture, code style, and testing guidance.
