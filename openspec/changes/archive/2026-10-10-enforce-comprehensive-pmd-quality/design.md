## Context

The Maven PMD plugin currently loads the project ruleset and an additions ruleset. The additions ruleset already establishes explicit local types and final locals/parameters; the requested category expansion will surface violations across both production and test sources and must remain compatible with the existing architecture, formatting, static-analysis, and coverage gates.

## Goals / Non-Goals

**Goals:**

- Load all seven requested PMD Java category rulesets through the existing Maven PMD execution.
- Establish a clean, reproducible PMD baseline for production and test code.
- Correct violations in a behavior-preserving way, preferring small mechanical changes and targeted refactors where rules expose genuine maintainability risks.
- Keep exceptions narrow, documented, and rule-specific when a rule conflicts with an intentional design or test fixture.

**Non-Goals:**

- Changing runtime behavior, public CLI contracts, or architecture boundaries.
- Disabling whole categories to reduce the violation count.
- Replacing PMD with another static-analysis tool.

## Decisions

- **Use category-level PMD references in `config/pmd-ruleset.xml`.** The project owner moved the category references into the primary ruleset and explicitly approved editing `/config` for this change. Category coverage stays visible and versioned in one place; `pmd-ruleset-additions.xml` keeps its existing targeted rules. Adding individual rules only would make future category drift likely.
- **Exclude rules that contradict each other or the project's conventions, one by one, with the reason beside each `<exclude>`.** Excluded: `OnlyOneReturn` (forbids guard clauses and raises nesting that the complexity rules penalise), `AtLeastOneConstructor` (contradicts `UnnecessaryConstructor` in the same category), `LawOfDemeter` (flags ordinary fluent and Picocli API use), `ShortVariable`, `LongVariable` and `ShortMethodName` (flag conventional names such as `id` and descriptive ones such as `configurationReader`), `CommentDefaultAccessModifier` (package-private Dagger and test members are intentional), `AvoidFieldNameMatchingMethodName` (constant/accessor pairs such as `NAME`/`name()` are part of the gate API), `UseConcurrentHashMap` (flags maps with no concurrent access), `DoNotUseThreads` (a J2EE container rule; Sentinel is a CLI), and `LoosePackageCoupling` (inert without package configuration). No whole category is disabled.
- **Tune rather than exclude test-heavy rules, except assertion counts.** `AvoidDuplicateLiterals` skips annotations and reports literals repeated eight or more times. `UnitTestContainsTooManyAsserts` is excluded: the project owner judged the existing multi-assertion tests more readable than split ones. Tests otherwise stay under the same ruleset as production code.
- **Keep the ArchUnit `JavaClasses` allowance for `LooseCoupling`.** ArchUnit exposes no interface for imported classes.
- **Fix source before suppressing.** Simple style findings will be corrected directly; suppressions will be limited to demonstrably intentional cases and include the PMD rule name plus a concise rationale. This preserves signal from the expanded categories.
- **Include test sources.** The current build explicitly runs PMD on tests, and test code is part of the repository's quality baseline. Test-only findings will be corrected or narrowly justified rather than silently excluded.
- **Validate incrementally.** After each family of related fixes, run PMD and formatting, then finish with `verify-quality.sh` so coverage, architecture, Checkstyle, SpotBugs, and PMD remain aligned.

## Risks / Trade-offs

- [Risk] The expanded categories expose a large pre-existing violation backlog. → Mitigation: group remediation by rule family, keep commits/mechanical edits reviewable, and use the PMD report as the source of truth after each batch.
- [Risk] Mechanical changes to declarations or modifiers could alter overload resolution or reflection-visible metadata. → Mitigation: compile and run the full test suite after each batch; avoid changing public signatures beyond adding source-level `final`.
- [Risk] Some design rules may conflict with intentional fixtures or framework conventions. → Mitigation: use narrow rule-level suppressions only after documenting why a direct refactor is inappropriate.
- [Risk] Category definitions can change with PMD upgrades. → Mitigation: pin the plugin version already used by the project and keep the category list explicit.

## Migration Plan

1. Record the expanded category references and capture the initial PMD report.
2. Remediate violations by family, beginning with mechanical style and modifier findings, then correctness, design, concurrency, performance, security, and test findings.
3. Run `./mvnw -q pmd:check`, formatting, and focused tests throughout the work.
4. Run `./verify-quality.sh` and confirm the final PMD report has zero violations.
5. Roll back by reverting the category references and associated source-only fixes if the full baseline cannot be accepted as one release change.
