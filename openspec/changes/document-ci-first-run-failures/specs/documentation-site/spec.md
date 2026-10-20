# Spec Delta

## ADDED Requirements

### Requirement: CI guide covers common first-run failures

The CI/CD guide SHALL explain the setup and remediation for common first-run failures of the `ci` profile, including complete Git history for secret scanning, caches that survive failed jobs, offline Trivy execution after dependency resolution, Dependency-Check credentials and runtime, and native Sentinel installation.

#### Scenario: User follows the GitHub Actions example

- **WHEN** a user copies the GitHub Actions example for a project using the `ci` profile
- **THEN** the example includes a full checkout and failure-proof cache restore/save steps for vulnerability databases

#### Scenario: User encounters a common first-run failure

- **WHEN** a user reads the CI/CD guide after a Gitleaks history issue, cache miss after failure, Maven Central rate limit, slow Dependency-Check run, or source-build delay
- **THEN** the guide identifies the cause and provides the corresponding configuration or workflow remediation
