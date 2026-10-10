# comprehensive-pmd-quality Specification

## Purpose

Provides a comprehensive, deterministic PMD quality baseline so every supported Java source category is checked consistently before changes are accepted.

## Requirements

### Requirement: Comprehensive Java PMD coverage

The project quality process SHALL evaluate production and test Java sources against the best-practices, code-style, design, error-prone, multithreading, performance, and security PMD categories.

#### Scenario: Category rules are enabled

- **WHEN** the PMD quality check runs
- **THEN** each requested Java category is loaded and evaluated without configuration or rule-resolution errors

#### Scenario: A category violation is introduced

- **WHEN** a production or test source violates an enabled category rule
- **THEN** the PMD quality check reports the violation and fails the quality check

### Requirement: Zero-violation baseline

The project SHALL maintain zero PMD violations under the enabled category set.

#### Scenario: The repository is checked at the accepted baseline

- **WHEN** the complete verification workflow runs
- **THEN** PMD completes successfully with no violations

#### Scenario: Existing code is remediated

- **WHEN** the expanded ruleset is adopted
- **THEN** all violations in production and test sources are corrected or intentionally addressed through documented, rule-specific configuration

### Requirement: Explicit immutable local inputs

Eligible local variables, method parameters, and constructor parameters SHALL use `final`, and local variable declarations SHALL use explicit types rather than `var`.

#### Scenario: A parameter is not reassigned

- **WHEN** a method or constructor parameter is used without reassignment
- **THEN** the declaration is marked `final`

#### Scenario: A local variable uses inferred type syntax

- **WHEN** a local declaration uses `var`
- **THEN** the PMD quality check fails until the declaration uses an explicit type
