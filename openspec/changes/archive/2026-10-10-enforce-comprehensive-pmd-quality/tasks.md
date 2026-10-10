## 1. Establish the PMD baseline

- [x] 1.1 Add the seven requested Java category references to the additions ruleset without disabling existing final-local, explicit-type, or final-parameter rules.
- [x] 1.2 Run PMD and capture the initial violation report grouped by rule, source set, and category.
- [x] 1.3 Confirm Maven PMD evaluates both production and test sources and fails on violations.
- [x] 1.4 Exclude the contradictory and convention-conflicting rules listed in design.md with a documented reason each, tune `AvoidDuplicateLiterals`, exclude `UnitTestContainsTooManyAsserts`, and restore the ArchUnit `JavaClasses` allowance for `LooseCoupling`.

## 2. Remediate mechanical style findings

- [x] 2.1 Correct modifier ordering, declaration placement, naming, literal, import, diamond-operator, array-initializer, and numeric-literal findings without changing behavior.
- [x] 2.2 Correct remaining local-variable, method-parameter, constructor-parameter, and `var` findings while preserving explicit types and `final` requirements.
- [x] 2.3 Reformat changed Java sources with Spotless and compile production and test sources.

## 3. Remediate correctness and error-prone findings

- [x] 3.1 Fix assignment, cast, null-handling, exception-flow, warning-suppression, resource-management, class-loading, and serial-version findings.
- [x] 3.2 Replace unsafe exception signatures and raw exception handling with precise types and behavior-preserving error paths.
- [x] 3.3 Run focused tests for every corrected correctness/error-prone area and verify no new PMD findings are introduced.

## 4. Remediate design and maintainability findings

- [x] 4.1 Reduce excessive method/class complexity, method counts, nesting, coupling, and Law-of-Demeter violations through behavior-preserving extraction and responsibility-focused refactors.
- [x] 4.2 Add required constructors or restructure classes where the design rules identify missing construction guarantees.
- [x] 4.3 Resolve duplicate literals, short/long identifiers, data-class findings, and field/method naming conflicts with repository-consistent names.
- [x] 4.4 Keep architecture-boundary tests passing after structural refactors.

## 5. Remediate concurrency, performance, and security findings

- [x] 5.1 Correct thread, synchronization, mutable-state, and concurrent-collection findings without introducing blocking or lifecycle regressions.
- [x] 5.2 Correct allocation-in-loop, buffer-sizing, immutable-field, and other performance findings while retaining required output and ordering.
- [x] 5.3 Correct security-category findings and document any unavoidable narrow suppressions with the rule name and rationale.

## 6. Remediate test-source findings

- [x] 6.1 Keep existing multi-assertion tests (assertion-count rule excluded by owner decision) and reduce remaining test-only findings without weakening coverage.
- [x] 6.2 Correct test naming, constructors, imports, resource handling, and test fixture design findings.
- [x] 6.3 Preserve or improve the JaCoCo instruction coverage threshold while completing test remediation.

## 7. Validate and lock the baseline

- [x] 7.1 Re-run PMD until the report contains zero violations across all requested categories and both source sets.
- [x] 7.2 Run Spotless, Checkstyle, SpotBugs, ArchUnit, the full test suite, and `./verify-quality.sh`.
- [x] 7.3 Validate the OpenSpec change and record the final PMD rule/category configuration and verification results.
