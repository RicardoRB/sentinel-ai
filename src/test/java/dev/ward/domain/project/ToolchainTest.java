package dev.ward.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ToolchainTest {
  @Test
  void everyBuildToolBelongsToItsToolchain() {
    final Map<BuildTool, Toolchain> toolchains =
        Arrays.stream(BuildTool.values())
            .collect(Collectors.toMap(tool -> tool, BuildTool::toolchain));

    assertThat(toolchains)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                BuildTool.MAVEN, Toolchain.JVM,
                BuildTool.GRADLE, Toolchain.JVM,
                BuildTool.NPM, Toolchain.NODE,
                BuildTool.PNPM, Toolchain.NODE,
                BuildTool.YARN, Toolchain.NODE,
                BuildTool.POETRY, Toolchain.PYTHON,
                BuildTool.PIP, Toolchain.PYTHON,
                BuildTool.GO, Toolchain.GO,
                BuildTool.CARGO, Toolchain.RUST,
                BuildTool.DOTNET, Toolchain.DOTNET));
  }

  @Test
  void projectReportsTheToolchainOfItsBuildTool() {
    final Project project =
        new Project(Path.of("app"), Language.PYTHON, BuildTool.POETRY, Framework.NONE);

    assertThat(project.toolchain()).isEqualTo(Toolchain.PYTHON);
  }
}
