package dev.sentinel.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class OpenSpecIgnoreInspectionTest {
  private final SystemEnvironmentInspection inspection =
      new SystemEnvironmentInspection(new ProcessCommandExecutor());

  @TempDir Path root;

  @Test
  void ignoredDirectoryIsReported() throws IOException {
    gitInit();
    Files.writeString(root.resolve(".gitignore"), "openspec/\n");

    assertThat(inspection.isOpenSpecDirectoryIgnored(root)).isTrue();
  }

  @Test
  void trackedDirectoryIsNotIgnored() throws IOException {
    gitInit();

    assertThat(inspection.isOpenSpecDirectoryIgnored(root)).isFalse();
  }

  @Test
  void projectOutsideGitIsNotIgnored() throws IOException {
    Files.createDirectories(root.resolve("openspec"));

    assertThat(inspection.isOpenSpecDirectoryIgnored(root)).isFalse();
  }

  private void gitInit() throws IOException {
    new ProcessCommandExecutor().execute(List.of("git", "init", "-q"), root);
    Files.createDirectories(root.resolve("openspec"));
  }
}
