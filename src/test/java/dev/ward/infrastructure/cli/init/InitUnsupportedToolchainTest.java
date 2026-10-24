package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.ProjectSnapshot;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Init refuses a project of a toolchain it cannot configure instead of treating it as Maven. */
class InitUnsupportedToolchainTest {
  @TempDir Path root;

  @Test
  void projectOfAnUnsupportedToolchainFailsWithoutChangingFiles() throws IOException {
    final InitCommandFixture init = InitCommandFixture.repository(root);
    Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = \"demo\"\n");
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit = init.run("", "--integration", "none", "--gate", "tests", "--yes");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output())
        .contains("Poetry")
        .contains("Ward init supports Maven and Gradle projects");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }
}
