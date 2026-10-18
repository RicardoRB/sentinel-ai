# Tuning PMD

## Where the rules live

- **Maven:** `config/pmd-ruleset.xml`, wired in `pom.xml` through the `maven-pmd-plugin`
  `<rulesets><ruleset>${project.basedir}/config/pmd-ruleset.xml</ruleset></rulesets>`.
- **Gradle:** with a preset, init writes `config/pmd-ruleset.xml` and a
  `pmd { ruleSetFiles = files('config/pmd-ruleset.xml'); ruleSets = [] }` block that analyzes the
  `main` source set only. Without that block, for example in a build set up by an older Sentinel,
  Gradle uses its own default rule set. Check which rules the build actually uses before tuning, and
  tell the user when the Sentinel file is not in use.
- The gate command is in `sentinel.toml` under `[quality-gates.pmd]`, for example `./mvnw pmd:check`
  or `./gradlew pmdMain`.

A first line `<!-- managed-by: sentinel preset=<id> -->` means Sentinel owns the file. See the
ownership step in `SKILL.md` before editing it.

The strict preset references whole categories (`category/java/quickstart.xml`,
`category/java/bestpractices.xml`) plus single rules such as `MethodArgumentCouldBeFinal`,
`LocalVariableCouldBeFinal`, and `UseExplicitTypes`. Read the actual file.

## Reading the findings

```sh
sentinel check --profile ci --format json
```

Read the check whose `name` is `pmd`. Each finding has `rule` (for example `UnusedPrivateMethod`),
`file`, `line`, `message`, and usually a `helpUri`. For counts across the project, read
`target/pmd.xml` (Maven) or `build/reports/pmd/*.xml` (Gradle). Each `<violation>` has a `rule`
attribute.

## Classifying a change

| Change | Class |
|---|---|
| Add a rule or category, for example `category/java/errorprone.xml` | strengthen |
| Lower a rule property limit, for example `CognitiveComplexity` `reportLevel` 15 to 12 | strengthen |
| `<exclude name="..."/>` inside a category reference | weaken |
| Remove a rule or category reference | weaken |
| Raise a property limit, or lower a rule's `priority` so it no longer fails the build | weaken |
| Replace a whole category with a hand-picked list that drops rules | weaken (list what is dropped) |
| Add a `<description>` or XML comments | neutral |

## Grounding proposals in the code

- **A whole category produces many findings of one rule** that conflicts with the project's
  conventions. For example, `bestpractices` `GuardLogStatement` in a project that only uses
  parameterized SLF4J logging. Show the count, and explain why the rule does not apply. Offer an
  `<exclude>` for that rule only. That is a weakening, so ask for it separately.
- **Rules that overlap with Checkstyle or the compiler,** for example unused imports. Point out the
  duplicate signal. Removing one side is a weakening, so ask for it separately.
- **Strengthening:** when a useful category, such as `errorprone`, is not enabled, do not switch it
  on just to see what happens. Look for the patterns it catches with `grep`, such as empty `catch`
  blocks or `==` on strings. Propose adding it with that evidence and an estimate of how many
  findings it will produce.

Never add `@SuppressWarnings("PMD...")`, `// NOPMD`, or `<exclude-pattern>` entries to hide files.
Those are suppressions.
