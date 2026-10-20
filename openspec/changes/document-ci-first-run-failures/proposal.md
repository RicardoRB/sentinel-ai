# Proposal

## Why

The CI/CD guide gives users a runnable GitHub Actions example but omits several first-run failure modes that are especially likely with the `ci` profile. This leads to misleading secret-scan results, repeated vulnerability-database downloads, Maven Central rate-limit failures, very slow dependency checks, and unnecessary attempts to build Sentinel from source.

## What Changes

- Update the GitHub Actions example with a full checkout for Gitleaks.
- Show failure-proof restore/save cache steps for Trivy and optional OWASP Dependency-Check data.
- Explain that Trivy should run offline after build-tool gates when the dependency tree is already available locally.
- Add a concise “Common CI failures” section covering Gitleaks history, cache persistence, Maven Central `429` responses, Dependency-Check credentials and runtime, and native Sentinel installation.
- Clarify that the recommended CI installation path is the verified native binary installer, not a source build.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-site`: Require the CI/CD guide to document common first-run `ci` profile failures and their provider-specific remediations.

## Impact

- Documentation only: `website/src/content/docs/guides/ci-cd.mdx`.
- No CLI behavior, gate generation, workflow behavior, dependencies, or public command/API contracts change.
- The existing repository workflow and generated Trivy configuration already implement the operational fixes; this change makes those practices discoverable to users.
