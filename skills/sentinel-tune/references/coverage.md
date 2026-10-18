# Tuning the coverage threshold

## Where the threshold lives

- **Maven:** in `pom.xml`, inside the `jacoco-maven-plugin` `<configuration><rules>`. Each `<limit>`
  has a `<counter>` (`LINE`, `BRANCH`, `INSTRUCTION`, `METHOD`, `CLASS`, `COMPLEXITY`), a `<value>`
  (usually `COVEREDRATIO`), and a `<minimum>` from `0` to `1`. The gate runs `./mvnw jacoco:check`.
  - The strict preset writes `BRANCH` `0.75` and `LINE` `0.85`. A custom selection writes `LINE`
    `0.80`. Read the actual values from the file.
  - `jacoco:check` needs execution data from a test run. The plugin must also have a `prepare-agent`
    execution, and the tests must run first, for example in the `tests` gate. If `target/jacoco.exec`
    is missing, the check has nothing to measure. Report that instead of changing thresholds.
- **Gradle:** init applies the `jacoco` plugin, and the gate runs `./gradlew jacocoTestReport`, which
  only *reports*. Nothing fails on low coverage. Enforcing a threshold needs a
  `jacocoTestCoverageVerification { violationRules { rule { limit { minimum = 0.80 } } } }` block in
  the build file **and** a gate command that runs it. The command lives in `sentinel.toml`. Propose
  both edits together as one strengthening, and tell the user that a later
  `sentinel init --overwrite` regenerates `sentinel.toml` with the default command.

Init leaves the configuration of a JaCoCo plugin that already exists untouched, so threshold edits in
the build file survive later `sentinel init` runs.

## Reading the current coverage

Read the measured ratios from the report, not from memory:

- Maven: `target/site/jacoco/jacoco.xml`. The strict preset binds the `report` goal to the `test`
  phase, so the file exists only after a test run that passed. If tests fail, fix them or report
  that first: a ratio measured on a failing suite is not meaningful. The last `<counter type="LINE" missed=".." covered=".."/>`
  elements directly under `<report>` are the bundle totals. The ratio is
  `covered / (missed + covered)`.
- Gradle: `build/reports/jacoco/test/jacocoTestReport.xml`, with the same format.

The `coverage` check in `sentinel check --format json` has no structured findings. Its `output` field
holds the JaCoCo message, for example `lines covered ratio is 0.62, but expected minimum is 0.85`.

## Classifying a change

| Change | Class |
|---|---|
| Raise a `<minimum>`, or add a counter such as `BRANCH` | strengthen |
| Add a `PACKAGE` or `CLASS` rule for a critical package, for example `*.domain.*` at `0.90` | strengthen |
| Lower a `<minimum>`, or remove a counter or rule | weaken |
| Add `<excludes>` for classes or packages | weaken (it hides code from the measurement) |
| Switch from `COVEREDRATIO` to `MISSEDCOUNT` with a generous maximum | weaken unless shown to be equivalent |

## Grounding proposals in the code

- **Coverage is far above the threshold.** Propose raising the minimum close to the measured value,
  for example from `0.80` to `0.85` when the measured value is `0.88`. This locks in the gain.
- **Coverage is below the threshold and the user wants the gate to pass.** First offer to write
  tests for the least covered classes that hold real logic. Name them, based on the per-class
  counters in the report. Tests must assert behavior. A test that only executes code, without
  assertions, is not an option. Only if the user still wants it, propose lowering the minimum as a
  separate weakening. Show the old value, the new value, the measured value, and the reason, and
  suggest a date or milestone for raising it again.
- **Generated or trivial code drags the ratio down,** such as generated sources or DTOs. Excluding
  it is still a weakening. Name every pattern and the number of classes it removes.
