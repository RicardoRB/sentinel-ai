package dev.sentinel.application.spec;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.spec.GuardRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovedArtifactGuardDiagnosticsTest {

  @Test
  void diagnosticsOmitCommandsPatchesAndPaths(@TempDir final Path root) throws IOException {
    withPom(root, PLAIN_POM);
    Files.createDirectories(root.resolve("openspec/specs/secret-capability"));
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
    final Logger logger = Logger.getLogger(ApprovedArtifactGuardService.class.getName());
    logger.addHandler(handler);
    try {
      final ApprovedArtifactGuardService service = ApprovedArtifactGuardServiceTest.service();
      service.evaluate(
          GuardRequest.shell(root, "", "TOKEN=hunter2 rm -rf openspec/specs/secret-capability"));
      service.evaluate(
          GuardRequest.patch(
              root,
              "",
              "*** Begin Patch\n*** Delete File: openspec/specs/secret-capability/spec.md"));
      service.evaluate(GuardRequest.paths(root, "", List.of("src/App.java")));
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(messages)
        .anyMatch(
            message ->
                message.startsWith(
                    "event=spec-guard-decision kind=shell targets=1 refusals=1"
                        + " reasons=approved_spec decision=refused durationMs="))
        .allSatisfy(
            message -> {
              assertThat(message).startsWith("event=spec-guard-decision");
              assertThat(message).doesNotContain("hunter2", "secret", "rm ", "App.java", "Patch");
            });
  }
}
