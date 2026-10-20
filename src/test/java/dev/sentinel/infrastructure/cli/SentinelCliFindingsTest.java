package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;

class SentinelCliFindingsTest extends SentinelCliIntegrationSupport {
  // configureFailingGates() configures checkstyle, gitleaks, and tests; SARIF has a run per gate.
  private static final int CONFIGURED_GATES = 3;
  private static final int JAVADOC_LINE = 42;
  private static final String SARIF = "sarif";
  private static final String OUTPUT_OPTION = "--output";
  private static final String SECRET = "ghp_FAKEFAKEFAKEFAKEFAKEFAKEFAKEFAKE1234";

  private void script(final String name, final String body) throws IOException {
    final Path file = project.resolve(name);
    Files.writeString(file, "#!/bin/sh\n" + body);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  private void configureFailingGates() throws IOException {
    script(
        "fake-checkstyle",
        """
        mkdir -p target
        cat > target/checkstyle-result.xml <<'XML'
        <checkstyle><file name="src/main/java/Foo.java">
        <error line="42" column="5" severity="error" message="Missing a Javadoc comment."
          source="com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocMethodCheck"/>
        </file></checkstyle>
        XML
        echo "[ERROR] You have 1 Checkstyle violation."
        exit 1
        """);
    script(
        "fake-gitleaks",
        """
        # Writes to the --report-path value ($2) without creating directories, like gitleaks.
        cat > "$2" <<JSON
        {"runs":[{"results":[{"ruleId":"github-pat","message":{"text":"secret %s"},
          "locations":[{"physicalLocation":{"artifactLocation":{"uri":"config/app.yml"},
          "region":{"startLine":3,"snippet":{"text":"%s"}}}}]}]}]}
        JSON
        exit 1
        """
            .formatted(SECRET, SECRET));
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
        version = 1
        [quality-gates.checkstyle]
        command = ["./fake-checkstyle"]
        [quality-gates.gitleaks]
        command = ["./fake-gitleaks", "--report-path", "target/gitleaks.sarif"]
        [quality-gates.tests]
        command = "./mvnw test"
        """);
  }

  @Test
  void jsonReportCarriesFindingsForFailedGates() throws IOException {
    configureFailingGates();

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    final JsonTree checks = JsonTree.parse(out.toString()).get(CHECKS);
    final JsonTree checkstyle = checks.get(0);
    assertThat(checkstyle.get("findingsTotal").asInt()).isEqualTo(1);
    final JsonTree finding = checkstyle.get("findings").get(0);
    assertThat(finding.get("rule").asString()).isEqualTo("MissingJavadocMethod");
    assertThat(finding.get("file").asString()).isEqualTo("src/main/java/Foo.java");
    assertThat(finding.get("line").asInt()).isEqualTo(JAVADOC_LINE);
    final JsonTree gitleaks = checks.get(1);
    assertThat(gitleaks.get("findings").get(0).get("file").asString()).isEqualTo("config/app.yml");
    assertThat(checks.get(2).get("findings").size()).isZero();
    assertThat(project.resolve("target")).isDirectory();

    out.getBuffer().setLength(0);
    final int errorExit =
        run(CHECK, FORMAT_OPTION, JSON, "--severity", "error", "-C", project.toString());
    final JsonTree errorReport = JsonTree.parse(out.toString());
    out.getBuffer().setLength(0);

    final int infoExit =
        run(CHECK, FORMAT_OPTION, JSON, "--severity", "info", "-C", project.toString());
    final JsonTree infoReport = JsonTree.parse(out.toString());

    assertThat(errorExit).isEqualTo(infoExit).isEqualTo(ExitCodes.FAILED);
    assertThat(errorReport.get(STATUS).asString()).isEqualTo(infoReport.get(STATUS).asString());
    assertThat(errorReport.get("policies").toString())
        .isEqualTo(infoReport.get("policies").toString());
    assertThat(run(CHECK, "--severity", "debug", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("error", "warning", "info");
  }

  @Test
  void sarifReportGoesToStdoutWithoutSecrets() throws IOException {
    configureFailingGates();

    final int exit = run(CHECK, FORMAT_OPTION, SARIF, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    final String stdout = out.toString();
    assertThat(stdout).doesNotContain(SECRET);
    final JsonTree log = JsonTree.parse(stdout);
    assertThat(log.get("version").asString()).isEqualTo("2.1.0");
    assertThat(log.get("runs").size()).isEqualTo(CONFIGURED_GATES);
    assertThat(log.get("runs").get(0).get("results").get(0).get("ruleId").asString())
        .isEqualTo("MissingJavadocMethod");
    assertThat(log.get("runs").get(2).get("results").size()).isZero();
  }

  @Test
  void outputFileReceivesSarifWhileStdoutShowsText() throws IOException {
    configureFailingGates();
    final Path report = project.resolve("reports/nested/sentinel.sarif");

    final int exit =
        run(
            CHECK,
            FORMAT_OPTION,
            SARIF,
            OUTPUT_OPTION,
            report.toString(),
            "-C",
            project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains("Quality Gate: FAILED").doesNotContain("\"runs\"");
    assertThat(JsonTree.parse(Files.readString(report)).get("runs").size())
        .isEqualTo(CONFIGURED_GATES);
  }

  @Test
  void outputFileReceivesJson() throws IOException {
    pass();
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        "version = 1\n[quality-gates.tests]\ncommand = \"./mvnw test\"\n");
    final Path report = project.resolve("sentinel.json");

    assertThat(
            run(
                CHECK,
                FORMAT_OPTION,
                JSON,
                OUTPUT_OPTION,
                report.toString(),
                "-C",
                project.toString()))
        .isZero();
    assertThat(out.toString()).contains("Quality Gate: PASSED");
    assertThat(JsonTree.parse(Files.readString(report)).get(STATUS).asString()).isEqualTo("PASSED");
  }

  @Test
  void outputWithTextFormatIsUsageErrorAndWritesNothing() {
    final Path report = project.resolve("report.txt");

    final int exit =
        run(
            CHECK,
            FORMAT_OPTION,
            "text",
            OUTPUT_OPTION,
            report.toString(),
            "-C",
            project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("--output requires --format json or --format sarif");
    assertThat(report).doesNotExist();
  }

  @Test
  void unknownFormatNamesAcceptedValues() {
    assertThat(run(CHECK, FORMAT_OPTION, "xml", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("text", "json", "sarif");
  }

  @Test
  void unwritableOutputIsAnError() throws IOException {
    pass();
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        "version = 1\n[quality-gates.tests]\ncommand = \"./mvnw test\"\n");
    Files.writeString(project.resolve("blocker"), "a file, not a directory");

    final int exit =
        run(
            CHECK,
            FORMAT_OPTION,
            SARIF,
            OUTPUT_OPTION,
            project.resolve("blocker/report.sarif").toString(),
            "-C",
            project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Cannot write report");
  }

  @Test
  void sarifErrorBeforeReportWritesNothingToStdout() {
    final int exit = run(CHECK, FORMAT_OPTION, SARIF, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isNotBlank();
  }
}
