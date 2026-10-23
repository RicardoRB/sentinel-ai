package dev.ward.infrastructure.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class OpenApiSpecInspectionTest {
  private static final String RESOURCES_SPEC = "src/main/resources/openapi.yml";
  private final ProcessCommandExecutor executor = new ProcessCommandExecutor();
  private final SystemEnvironmentInspection inspection = new SystemEnvironmentInspection(executor);

  @TempDir Path root;

  @Test
  void detectsTrackedSpecUnderResources() throws IOException {
    gitInit();
    track(RESOURCES_SPEC);
    track("openapi.yaml");

    assertThat(inspection.openApiSpec(root)).contains(RESOURCES_SPEC);
  }

  @Test
  void detectsTrackedSpecAtProjectRoot() throws IOException {
    gitInit();
    track("openapi.yaml");

    assertThat(inspection.openApiSpec(root)).contains("openapi.yaml");
  }

  @Test
  void ignoresUntrackedSpec() throws IOException {
    gitInit();
    write(RESOURCES_SPEC);

    assertThat(inspection.openApiSpec(root)).isEmpty();
  }

  @Test
  void projectWithoutSpecOrGitHasNone() {
    assertThat(inspection.openApiSpec(root)).isEmpty();
  }

  private void gitInit() {
    executor.execute(List.of("git", "init", "-q"), root);
  }

  private void track(final String path) throws IOException {
    write(path);
    executor.execute(List.of("git", "add", "--", path), root);
  }

  private void write(final String path) throws IOException {
    final Path file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "openapi: 3.0.3\n");
  }
}
