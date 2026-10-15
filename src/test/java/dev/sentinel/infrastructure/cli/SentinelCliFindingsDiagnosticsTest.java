package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentinelCliFindingsDiagnosticsTest extends SentinelCliIntegrationSupport {
  private static final String SECRET = "ghp_FAKEFAKEFAKEFAKEFAKEFAKEFAKEFAKE1234";

  private void script(final String name, final String body) throws IOException {
    final Path file = project.resolve(name);
    Files.writeString(file, "#!/bin/sh\n" + body);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  @Test
  void findingDiagnosticsNameEventsButNeverContentsOrPaths() throws IOException {
    script(
        "fake-gitleaks",
        """
        cat > "$2" <<JSON
        {"runs":[{"results":[{"ruleId":"github-pat","message":{"text":"%1$s"},
          "locations":[{"physicalLocation":{"artifactLocation":{"uri":"config/app.yml"},
          "region":{"startLine":3,"snippet":{"text":"%1$s"}}}}]}]}]}
        JSON
        exit 1
        """
            .formatted(SECRET));
    script("fake-pmd", "echo 'PMD Failure: secret-rule'\nexit 1\n");
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
        version = 1
        [quality-gates.gitleaks]
        command = ["./fake-gitleaks", "--report-path", "target/gitleaks.sarif"]
        [quality-gates.pmd]
        command = ["./fake-pmd"]
        """);
    final Path report = project.resolve("out/sentinel.sarif");

    run(
        "--verbose",
        CHECK,
        FORMAT_OPTION,
        "sarif",
        "--output",
        report.toString(),
        "-C",
        project.toString());
    final List<String> messages = err.toString().lines().toList();

    assertThat(messages)
        .anyMatch(message -> message.contains("event=report-directory-created dir=target"))
        .anyMatch(message -> message.contains("event=findings-extract gate=gitleaks"))
        .anyMatch(
            message ->
                message.endsWith("event=findings-attached gate=gitleaks total=1 truncated=false"))
        .anyMatch(
            message -> message.endsWith("event=finding-report-missing gate=pmd report=pmd.xml"))
        .anyMatch(message -> message.contains("event=report-written file=sentinel.sarif"))
        .allSatisfy(
            message ->
                assertThat(message)
                    .doesNotContain(SECRET, "config/app.yml", "secret-rule", project.toString()));
  }
}
