package dev.sentinel.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.gate.ReportWriteException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemReportDirectoriesTest {
  @TempDir Path root;

  private final FileSystemReportDirectories directories = new FileSystemReportDirectories();

  @Test
  void createsMissingParentInsideProject() {
    directories.prepareParent(root, "target/gitleaks.sarif");

    assertThat(root.resolve("target")).isDirectory();
    assertThat(root.resolve("target/gitleaks.sarif")).doesNotExist();
  }

  @Test
  void createsNestedParentsAndToleratesExistingOnes() {
    directories.prepareParent(root, "build/reports/gitleaks/out.sarif");
    directories.prepareParent(root, "build/reports/gitleaks/out.sarif");

    assertThat(root.resolve("build/reports/gitleaks")).isDirectory();
  }

  @Test
  void ignoresPathsOutsideTheProjectAndRootLevelFiles() throws Exception {
    final Path project = root.resolve("project");
    directories.prepareParent(root, "project/marker");

    directories.prepareParent(project, "../elsewhere/report.sarif");
    directories.prepareParent(project, "/tmp/sentinel-outside/report.sarif");
    directories.prepareParent(project, "report.sarif");
    directories.prepareParent(project, "bad\0path/report.sarif");

    assertThat(root.resolve("elsewhere")).doesNotExist();
    assertThat(Path.of("/tmp/sentinel-outside")).doesNotExist();
    try (Stream<Path> entries = Files.list(project)) {
      assertThat(entries).isEmpty();
    }
  }

  @Test
  void reportsCreationFailure() throws Exception {
    Files.writeString(root.resolve("target"), "a file, not a directory");

    assertThatThrownBy(() -> directories.prepareParent(root, "target/sub/report.sarif"))
        .isInstanceOf(ReportWriteException.class);
  }
}
