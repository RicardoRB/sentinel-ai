package dev.sentinel.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingSeverity;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlReportFindingsTest {
  private static final String CHECKSTYLE_REPORT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <checkstyle version="10.17.0">
        <file name="%s/src/main/java/Foo.java">
          <error line="42" column="5" severity="error" message="Missing a Javadoc comment."
            source="com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocMethodCheck"/>
          <error line="88" severity="warning" message="Line is longer than 100 characters."
            source="com.puppycrawl.tools.checkstyle.checks.sizes.LineLengthCheck"/>
        </file>
        <file name="src/main/java/Bar.java">
          <error line="1" severity="info" message="Note." source="com.example.CustomCheck"/>
        </file>
      </checkstyle>
      """;

  private static final String PMD_REPORT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <pmd version="7.0.0">
        <file name="%s/src/main/java/Foo.java">
          <violation beginline="10" endline="10" begincolumn="9" endcolumn="20"
            rule="UnusedLocalVariable" ruleset="Best Practices" priority="3"
            externalInfoUrl="https://docs.pmd-code.org/latest/pmd_rules_java_bestpractices.html#unusedlocalvariable">
      Avoid unused local variables such as 'x'.
          </violation>
          <violation beginline="20" rule="EmptyCatchBlock" priority="1">Avoid empty catch blocks</violation>
          <violation beginline="30" rule="ShortVariable" priority="5">Avoid short names</violation>
        </file>
        <error filename="Broken.java" msg="ParseException"/>
      </pmd>
      """;

  @TempDir Path root;

  @Test
  void mavenCheckstyleReportBecomesLocatedFindings() {
    FindingFixtures.write(
        root.resolve("target/checkstyle-result.xml"), CHECKSTYLE_REPORT.formatted(root));

    final List<Finding> findings =
        new CheckstyleXmlFindings()
            .extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("checkstyle", ""),
                FindingFixtures.justBefore());

    assertThat(findings).hasSize(3);
    final Finding first = findings.getFirst();
    assertThat(first.ruleId()).isEqualTo("MissingJavadocMethod");
    assertThat(first.file()).isEqualTo("src/main/java/Foo.java");
    assertThat(first.line()).isEqualTo(42);
    assertThat(first.column()).isEqualTo(5);
    assertThat(first.severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(first.helpUri())
        .isEqualTo("https://checkstyle.org/checks/javadoc/missingjavadocmethod.html");
    assertThat(findings.get(1).severity()).isEqualTo(FindingSeverity.WARNING);
    assertThat(findings.get(1).column()).isNull();
    assertThat(findings.get(2).file()).isEqualTo("src/main/java/Bar.java");
    assertThat(findings.get(2).severity()).isEqualTo(FindingSeverity.NOTE);
    assertThat(findings.get(2).ruleId()).isEqualTo("Custom");
    assertThat(findings.get(2).helpUri()).isNull();
  }

  @Test
  void gradleCheckstyleReportsAreReadPerSourceSet() {
    FindingFixtures.write(
        root.resolve("build/reports/checkstyle/main.xml"), CHECKSTYLE_REPORT.formatted(root));
    FindingFixtures.write(
        root.resolve("build/reports/checkstyle/test.xml"),
        "<checkstyle><file name=\"T.java\"><error line=\"3\" message=\"m\""
            + " source=\"com.puppycrawl.tools.checkstyle.checks.FinalParametersCheck\"/>"
            + "</file></checkstyle>");

    final List<Finding> findings =
        new CheckstyleXmlFindings()
            .extract(
                FindingFixtures.gradle(root),
                FindingFixtures.failed("checkstyle", ""),
                FindingFixtures.justBefore());

    assertThat(findings).hasSize(4);
    assertThat(findings.getLast().helpUri())
        .isEqualTo("https://checkstyle.org/checks/misc/finalparameters.html");
  }

  @Test
  void staleReportIsIgnored() {
    final Path report =
        FindingFixtures.write(
            root.resolve("target/checkstyle-result.xml"), CHECKSTYLE_REPORT.formatted(root));
    FindingFixtures.age(report, Duration.ofHours(1));

    assertThat(
            new CheckstyleXmlFindings()
                .extract(
                    FindingFixtures.maven(root),
                    FindingFixtures.failed("checkstyle", ""),
                    FindingFixtures.justBefore()))
        .isEmpty();
  }

  @Test
  void missingReportYieldsNoFindings() {
    assertThat(
            new PmdXmlFindings()
                .extract(
                    FindingFixtures.maven(root),
                    FindingFixtures.failed("pmd", ""),
                    FindingFixtures.justBefore()))
        .isEmpty();
    assertThat(
            new PmdXmlFindings()
                .extract(
                    FindingFixtures.gradle(root),
                    FindingFixtures.failed("pmd", ""),
                    FindingFixtures.justBefore()))
        .isEmpty();
  }

  @Test
  void malformedReportIsReportedWithoutContents() {
    FindingFixtures.write(root.resolve("target/pmd.xml"), "<pmd><file name=");

    assertThatThrownBy(
            () ->
                new PmdXmlFindings()
                    .extract(
                        FindingFixtures.maven(root),
                        FindingFixtures.failed("pmd", ""),
                        FindingFixtures.justBefore()))
        .isInstanceOf(MalformedReportException.class)
        .hasMessage("Malformed report pmd.xml");
  }

  @Test
  void mavenPmdReportMapsPriorityAndRuleDocumentation() {
    FindingFixtures.write(root.resolve("target/pmd.xml"), PMD_REPORT.formatted(root));

    final List<Finding> findings =
        new PmdXmlFindings()
            .extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("pmd", ""),
                FindingFixtures.justBefore());

    assertThat(findings).hasSize(3);
    final Finding first = findings.getFirst();
    assertThat(first.ruleId()).isEqualTo("UnusedLocalVariable");
    assertThat(first.message()).isEqualTo("Avoid unused local variables such as 'x'.");
    assertThat(first.file()).isEqualTo("src/main/java/Foo.java");
    assertThat(first.line()).isEqualTo(10);
    assertThat(first.column()).isEqualTo(9);
    assertThat(first.severity()).isEqualTo(FindingSeverity.WARNING);
    assertThat(first.helpUri()).startsWith("https://docs.pmd-code.org/");
    assertThat(findings.get(1).severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(findings.get(1).helpUri()).isNull();
    assertThat(findings.get(2).severity()).isEqualTo(FindingSeverity.NOTE);
  }

  @Test
  void gradlePmdReportIsRead() {
    FindingFixtures.write(root.resolve("build/reports/pmd/main.xml"), PMD_REPORT.formatted(root));

    assertThat(
            new PmdXmlFindings()
                .extract(
                    FindingFixtures.gradle(root),
                    FindingFixtures.failed("pmd", ""),
                    FindingFixtures.justBefore()))
        .hasSize(3);
  }

  @Test
  void externalEntitiesAreNotResolved() {
    final Path secret = FindingFixtures.write(root.resolve("secret.txt"), "TOP-SECRET-VALUE");
    FindingFixtures.write(
        root.resolve("target/checkstyle-result.xml"),
        """
        <?xml version="1.0"?>
        <!DOCTYPE checkstyle [<!ENTITY xxe SYSTEM "file://%s">]>
        <checkstyle><file name="A.java">
          <error line="1" message="&xxe;" source="x.YCheck"/>
        </file></checkstyle>
        """
            .formatted(secret));

    List<Finding> findings;
    try {
      findings =
          new CheckstyleXmlFindings()
              .extract(
                  FindingFixtures.maven(root),
                  FindingFixtures.failed("checkstyle", ""),
                  FindingFixtures.justBefore());
    } catch (MalformedReportException rejected) {
      findings = List.of();
    }

    assertThat(findings).noneMatch(finding -> finding.message().contains("TOP-SECRET-VALUE"));
  }

  @Test
  void relativizeKeepsOutsidePathsAndHandlesFileUris() {
    assertThat(
            ReportFiles.relativize(FindingFixtures.maven(root), "file://" + root + "/src/A.java"))
        .isEqualTo("src/A.java");
    assertThat(ReportFiles.relativize(FindingFixtures.maven(root), "/elsewhere/A.java"))
        .isEqualTo("/elsewhere/A.java");
    assertThat(ReportFiles.relativize(FindingFixtures.maven(root), " ")).isNull();
    assertThat(ReportFiles.relativize(FindingFixtures.maven(root), null)).isNull();
  }

  @Test
  void ruleIdFallsBackToGateWhenSourceIsMissing() {
    assertThat(CheckstyleXmlFindings.ruleId(null)).isEqualTo("checkstyle");
    assertThat(CheckstyleXmlFindings.ruleId("Check")).isEqualTo("Check");
    assertThat(CheckstyleXmlFindings.helpUri(null)).isNull();
  }
}
