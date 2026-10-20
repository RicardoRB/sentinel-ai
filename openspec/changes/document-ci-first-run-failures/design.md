# Design

## Context

The existing CI/CD guide contains provider examples and a generic `sentinel check --profile ci` invocation. The repository's own GitHub Actions workflow demonstrates the more reliable pattern: full Git history, explicit Trivy cache restore/save, an offline Trivy scan after build-tool gates, and installation of the released native binary. Dependency-Check remains a documented optional gate even though it is disabled in the repository configuration.

## Goals / Non-Goals

**Goals:**

- Make the GitHub Actions example safe for the first CI run and for failed jobs.
- Explain the relationship between build-tool dependency resolution, Trivy offline mode, and Maven Central rate limits.
- Give users short, actionable remediations for the failure modes named in the proposal.
- Keep the guide provider-neutral where the advice is not GitHub-specific.

**Non-Goals:**

- Change generated gate commands or Sentinel runtime behavior.
- Re-enable Dependency-Check in the repository's own CI.
- Add a reusable GitHub Action, new workflow, or new cache mechanism to the product.
- Document every possible CI provider's cache syntax in detail.

## Decisions

### Use the existing GitHub Actions example as the canonical workflow

The guide already presents GitHub Actions first, and the repository workflow supplies a verified reference for the required ordering and cache behavior. The example will use `actions/cache/restore` before the check and `actions/cache/save` with `if: always()` after it. This is preferred over the shorter combined `actions/cache` action because a failed gate must still leave a reusable database cache.

### Show both Trivy and optional Dependency-Check cache locations

The Trivy database is relevant to the default pattern described by the issue. Dependency-Check data must also be covered because users may enable that gate, but its cache is presented as optional rather than implying that the repository enables it. The guide will use the Maven Dependency-Check data directory and explicitly connect credentials to environment variables.

### Recommend the native installer for Sentinel itself

CI users need the Sentinel executable, not a Java build of Sentinel. The example will retain the verified installer flow and explain that project runtimes such as Java, Maven, Node, or Python are separate prerequisites for selected gates.

### Add a focused troubleshooting section rather than expanding every provider example

The same failure causes apply across CI providers, while cache syntax differs. A focused section keeps the advice discoverable without duplicating provider-specific YAML in GitLab, Jenkins, CircleCI, Azure, and Bitbucket examples.

## Risks / Trade-offs

- **Risk:** The cache key examples could restore stale or incompatible vulnerability data. **Mitigation:** Use provider/run-scoped save keys with restore prefixes, and describe these as database caches rather than correctness substitutes.
- **Risk:** Offline Trivy can miss dependencies that were not resolved by the build tool. **Mitigation:** State that offline mode is appropriate after build-tool gates and identify SBOM scanning or online scanning as stricter alternatives when dependency completeness matters.
- **Risk:** Dependency-Check guidance could encourage exposing credentials. **Mitigation:** Name environment variables only and explicitly prohibit committing keys to configuration or workflow source.
- **Risk:** Documentation can drift from the repository workflow. **Mitigation:** Validate the final example and links with the website build and compare the commands against `.github/workflows/verify.yml` and `sentinel.toml` during implementation.
