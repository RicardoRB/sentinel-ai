## Why

Sentinel currently enables only a limited PMD ruleset plus a few targeted style rules, allowing broad classes of maintainability, correctness, security, and concurrency problems to pass the quality gate. Expanding PMD coverage now establishes a consistent static-analysis baseline while the codebase is being actively standardized around explicit, immutable local state and parameters.

## What Changes

- Enable the requested PMD Java categories: best practices, code style, design, error prone, multithreading, performance, and security.
- Remediate all violations reported by the expanded ruleset in production and test sources.
- Preserve and formalize the existing requirements that eligible locals, method parameters, and constructor parameters use `final`.
- Preserve the existing prohibition on local `var` declarations by using explicit declared types.
- Keep PMD, formatting, tests, architecture checks, static analysis, and coverage passing through `verify-quality.sh`.

## Capabilities

### New Capabilities

- `comprehensive-pmd-quality`: Enforces the complete requested PMD category set and maintains a zero-violation project baseline.

### Modified Capabilities

None.

## Impact

- PMD configuration and Maven quality-gate wiring.
- Java production and test sources affected by the newly enabled rules.
- Build and verification workflows; no runtime API or user-facing CLI behavior is intended to change.
