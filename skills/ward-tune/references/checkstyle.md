# Tuning Checkstyle

## Where the rules live

- **Maven:** `config/checkstyle.xml`, wired in `pom.xml` through the `maven-checkstyle-plugin`
  `<configLocation>${project.basedir}/config/checkstyle.xml</configLocation>`. The plugin's
  `<violationSeverity>` decides which severity fails the build. Init writes `error`.
- **Gradle:** with a preset, init writes `config/checkstyle.xml` and a
  `checkstyle { configFile = file('config/checkstyle.xml') }` block that analyzes the `main` source
  set only. Without that block, for example in a build set up by an older Ward, Gradle reads its
  default, `config/checkstyle/checkstyle.xml`. Check which file the build actually uses before you
  tune anything, and tell the user when the Ward file is not in use.
- The gate command is in `ward.toml` under `[quality-gates.checkstyle]`, for example
  `./mvnw checkstyle:check` or `./gradlew checkstyleMain`.
- The `[quality-gates.checkstyle.partial]` table init writes narrows the per-edit check with Maven
  `-Dcheckstyle.includes=**/<file name>`, or on Gradle with `-PwardPaths=<paths>`, which the
  `tasks.named('checkstyleMain')` filter init writes with a preset reads. Keep `config` listing the
  rule file and build files, so editing them runs the gate in full. A root-relative
  `checkstyle.includes` analyzes no files and passes; verify any custom form against the real tool.

A first line `<!-- managed-by: ward preset=<id> -->` means Ward owns the file. See the
ownership step in `SKILL.md` before editing it.

## Reading the findings

Run the profile that includes the gate. `ci` includes every gate:

```sh
ward check --profile ci --format json
```

In the JSON, read the check whose `name` is `checkstyle`. Each entry in `findings` has `rule`
(for example `MagicNumber`), `file`, `line`, and `message`. At most 50 findings are listed.
`findingsTotal` is the real count. To count findings per rule across the whole project, read the full
XML report instead: `target/checkstyle-result.xml` (Maven) or `build/reports/checkstyle/*.xml`
(Gradle). Each `<error>` has a `source` attribute with the check's class name, for example
`com.puppycrawl.tools.checkstyle.checks.coding.MagicNumberCheck` for `MagicNumber`.

Present the evidence as a short table: rule, count, two or three example locations.

## Strict preset modules

The strict preset enables: `EqualsHashCode`, `MissingSwitchDefault`, `FallThrough`,
`EmptyCatchBlock`, `IllegalCatch`, `AvoidStarImport`, `UnusedImports`, `StringLiteralEquality`,
`OneStatementPerLine`, `MultipleVariableDeclarations`, `CyclomaticComplexity`, `MethodLength`,
`ParameterNumber`, `FileLength`, `MissingJavadocType`, `MissingJavadocMethod`, `MagicNumber`,
`HiddenField`, and the naming checks. Read the actual file, because the user may have changed it.

Limits are module properties, for example `<property name="max" value="10"/>` inside
`CyclomaticComplexity`. Strict sets `CyclomaticComplexity` 10, `MethodLength` 60, `ParameterNumber`
5, and `FileLength` 500. File-level checks such as `FileLength` and `LineLength` are children of
`Checker`, not `TreeWalker`. Checkstyle refuses to load the file otherwise.

## Classifying a change

| Change | Class |
|---|---|
| Add a module, for example `FinalParameters`, `NeedBraces`, `DeclarationOrder` | strengthen |
| Lower a limit: `CyclomaticComplexity` `max` from 10 to 8, `MethodLength` `max` from 150 to 80 | strengthen |
| Restrict a module's scope by configuring it, for example `MissingJavadocMethod` `scope=public` instead of all methods | weaken (it accepts more code) |
| Raise a limit: `ParameterNumber` `max` from 7 to 9 | weaken |
| Remove a module, or set its `severity` to `warning` or `ignore` | weaken |
| Add `ignoreNumbers`, `ignoreHashCodeMethod`, or similar exemptions | weaken |
| Reorder modules, add XML comments that explain why a module is configured | neutral |

## Grounding proposals in the code

- **One rule dominates the findings.** Show the count. Offer to fix the code, which keeps the rule,
  or to change the rule, which is a weakening. Fixing the code is the default recommendation when
  the findings point to real problems, such as `EmptyCatchBlock` or `StringLiteralEquality`.
- **Convention rules the team clearly does not follow,** for example `MissingJavadocMethod` on a
  codebase without Javadoc. Narrowing the scope to `public` methods of public types may fit. It is
  still a weakening, so ask for it separately and record the reason in an XML comment.
- **A rule passes with zero findings, and a stricter limit would also pass.** Tightening it, for
  example lowering `CyclomaticComplexity` `max` to the current maximum, keeps the code where it is.
  It is a strengthening. Show the current maximum as evidence.

Never add `@SuppressWarnings("checkstyle:...")`, `// CHECKSTYLE:OFF`, a `SuppressionFilter`, or a
`suppressions.xml` file. Those are suppressions, and this skill does not add them.
