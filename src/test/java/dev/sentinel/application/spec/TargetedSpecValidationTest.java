package dev.sentinel.application.spec;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.spec.SpecReportRenderer;
import dev.sentinel.domain.spec.SpecValidationReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetedSpecValidationTest extends SpecValidationTestSupport {
  private static final String FOO = "openspec/changes/foo";
  private static final String FOO_PROPOSAL = FOO + "/proposal.md";

  @Test
  void targetedValidationReportsFindingsForTheEditedChange() throws IOException {
    project(BOTH_ENABLED, FOO);
    result = new CommandResult(1, "", INVALID, Duration.ZERO);

    final SpecValidationReport report =
        service().validatePaths(root, List.of(), List.of(FOO_PROPOSAL));

    assertThat(commands)
        .containsExactly(
            List.of(
                OPENSPEC, "validate", "foo", "--type", "change", "--strict", "--no-interactive"));
    assertThat(SpecReportRenderer.render(report))
        .isEqualTo(
            "openspec change foo: 1 error\n  ERROR x/spec.md: must include at least one scenario");
  }

  @Test
  void eachDistinctItemIsValidatedOnceAndEscapesAreIgnored() throws IOException {
    project(BOTH_ENABLED, FOO, "openspec/changes/bar");
    Files.createDirectories(root.resolve("openspec/specs/identity/user-auth"));
    Files.writeString(root.resolve("openspec/specs/identity/user-auth/spec.md"), "# spec");

    final SpecValidationReport report =
        service()
            .validatePaths(
                root,
                List.of(),
                List.of(
                    root.resolve(FOO_PROPOSAL).toString(),
                    FOO + "/specs/x/spec.md",
                    "openspec/changes/bar/design.md",
                    "openspec/specs/identity/user-auth/spec.md",
                    "openspec/changes/../../../outside/openspec/changes/foo/spec.md",
                    "src/main/java/App.java"));

    assertThat(commands)
        .extracting(command -> command.get(2))
        .containsExactly("foo", "bar", "identity/user-auth");
    assertThat(report.hasFindings()).isFalse();
  }

  @Test
  void archivedRemovedAndUnrelatedPathsValidateNothing() throws IOException {
    project(BOTH_ENABLED, "openspec/changes/archive/2026-10-05-old");

    final SpecValidationReport report =
        service()
            .validatePaths(
                root,
                List.of(),
                List.of(
                    "openspec/changes/archive/2026-10-05-old/tasks.md",
                    "openspec/changes/gone/tasks.md",
                    "README.md"));

    assertThat(report).isEqualTo(SpecValidationReport.EMPTY);
    assertThat(commands).isEmpty();
  }

  @Test
  void targetedValidationIsANoOpWithoutActiveRegistration() throws IOException {
    project(
        """
        version = 1
        [quality-gates.openspec-archived]
        command = ["openspec", "validate", "--archived", "--no-interactive"]
        """,
        FOO);

    assertThat(service().validatePaths(root, List.of(), List.of(FOO + "/tasks.md")))
        .isEqualTo(SpecValidationReport.EMPTY);
    assertThat(commands).isEmpty();
  }

  @Test
  void projectWithoutConfigurationIsANoOp() throws IOException {
    withPom(root, PLAIN_POM);
    Files.createDirectories(root.resolve(FOO));

    assertThat(service().validatePaths(root, List.of(), List.of(FOO + "/tasks.md")))
        .isEqualTo(SpecValidationReport.EMPTY);
    assertThat(service().validateConfigured(root, List.of())).isEqualTo(SpecValidationReport.EMPTY);
    assertThat(commands).isEmpty();
  }

  @Test
  void missingProjectIsReported(@TempDir final Path empty) {
    assertThatThrownBy(() -> service().validatePaths(empty, List.of(), List.of("a.md")))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void diagnosticsOmitFindingsVectorsAndPaths() throws IOException {
    project(BOTH_ENABLED, FOO);
    result = new CommandResult(1, "", INVALID, Duration.ZERO);
    final List<String> messages = new ArrayList<>();
    final Handler handler =
        new Handler() {
          @Override
          public void publish(final LogRecord logRecord) {
            messages.add(logRecord.getMessage());
          }

          @Override
          public void flush() {
            // Records are kept in memory.
          }

          @Override
          public void close() {
            // Nothing to release.
          }
        };
    final Logger logger = Logger.getLogger(SpecValidationService.class.getName());
    logger.addHandler(handler);
    try {
      service().validatePaths(root, List.of(), List.of(FOO_PROPOSAL));
      service().validateConfigured(root, List.of());
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(messages)
        .isNotEmpty()
        .allSatisfy(
            message -> {
              assertThat(message).startsWith("event=spec-validate-");
              assertThat(message)
                  .doesNotContain("scenario", "validate --", "proposal.md", "foo", "openspec ");
            });
  }
}
