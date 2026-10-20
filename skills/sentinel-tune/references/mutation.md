# Tuning mutation testing

## Where the configuration lives

- **Maven:** in `pom.xml`, the `pitest-maven` plugin with the `pitest-junit5-plugin` dependency. The
  strict preset writes `<mutationThreshold>60</mutationThreshold>` and no `<targetClasses>`, so PIT
  mutates every class under the project's group. The gate runs `./mvnw pitest:mutationCoverage`.
- **Gradle:** a `pitest { ... }` block with `pitestVersion`, `junit5PluginVersion`, and, for the
  strict preset, `mutationThreshold = 60`. The gate runs `./gradlew pitest`.

Init leaves an existing PIT configuration untouched, so edits survive later `sentinel init` runs.
PIT 1.30.1 or newer is needed to read Java 25 class files. An older version fails before it mutates
anything. That is a configuration problem, not a finding: report it and offer the version update.

Init writes no `partial` table for `mutation`: mutation testing runs on the whole project and only in
the `ci` profile.

## Reading the current results

Read `target/pit-reports/mutations.xml` (Maven) or `build/reports/pitest/mutations.xml` (Gradle)
after a run. Each `<mutation detected="true|false" status="KILLED|SURVIVED|NO_COVERAGE|TIMED_OUT">`
has `<mutatedClass>`, `<mutatedMethod>`, `<lineNumber>`, and `<mutator>`. Count per package:

- the mutation score, `detected / total`;
- `SURVIVED` mutants, which the tests ran but did not catch: weak assertions;
- `NO_COVERAGE` mutants, which no test ran.

The `mutation` check in `sentinel check --format json` has no structured findings. Its `output` field
holds PIT's summary, for example `Mutation score of 52 is below threshold of 60`.

Also note how long the gate takes. A run of several minutes is a cost the team pays on every CI run.

## Classifying a change

| Change | Class |
|---|---|
| Raise `mutationThreshold` | strengthen |
| Add a mutator group, for example `<mutators><mutator>STRONGER</mutator></mutators>` | strengthen |
| Set `threads` or `timeoutConstant` | neutral (same accepted code, different run time) |
| Lower `mutationThreshold` | weaken |
| Set `<targetClasses>` to fewer packages, or add `<excludedClasses>` / `excludedMethods` | weaken (it hides code from the measurement) |
| Remove mutators | weaken |

## Grounding proposals in the code

- **The run is slow.** Propose `threads` set to the CPU count of the CI runner (GitHub-hosted Linux
  runners have 4) as a neutral change. Show the measured run time before and after.
- **Adapters dominate the survivors.** When most `SURVIVED` and `NO_COVERAGE` mutants are in
  infrastructure packages (adapters, CLI, persistence) that integration tests cover, propose
  `<targetClasses>` limited to the domain and application packages found in the layout, for example
  `com.acme.shop.domain.*` and `com.acme.shop.application.*`. This is a **weakening**: show the
  per-package counts, the packages that would no longer be mutated, and ask for a reason.
- **The score is far above the threshold.** Propose raising the threshold close to the measured
  score, for example from `60` to `70` when the score is `74`.
- **The score is below the threshold.** First offer to strengthen the tests that let mutants survive.
  Name the classes and methods with the most `SURVIVED` mutants, and say which assertion is missing,
  for example "nothing checks the return value of `Pricing.discount`". Lowering the threshold comes
  only after the user declines that, as a separate weakening.
