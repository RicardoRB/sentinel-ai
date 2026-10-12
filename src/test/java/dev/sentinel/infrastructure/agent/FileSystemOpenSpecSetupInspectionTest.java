package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import dev.sentinel.domain.agent.OpenSpecSetup;
import dev.sentinel.domain.process.CommandExecutor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemOpenSpecSetupInspectionTest {
  private static final OpenSpecGuidanceLayout CLAUDE =
      OpenSpecGuidanceLayout.forAgent("claude-code").orElseThrow();
  private static final OpenSpecGuidanceLayout OPENCODE =
      OpenSpecGuidanceLayout.forAgent("opencode").orElseThrow();

  @TempDir Path project;
  @TempDir Path bin;

  @Test
  void reportsEveryPrerequisitePresent() throws IOException {
    executable(bin.resolve("openspec"));
    Files.createDirectory(project.resolve("openspec"));
    write(project.resolve(".claude/skills/openspec-verify-change/SKILL.md"));

    assertThat(inspection(bin.toString()).inspect(project, CLAUDE))
        .isEqualTo(new OpenSpecSetup(true, true, true));
  }

  @Test
  void reportsEveryPrerequisiteMissing() {
    assertThat(inspection(bin.toString()).inspect(project, CLAUDE))
        .isEqualTo(new OpenSpecSetup(false, false, false));
    assertThat(inspection(null).inspect(project, CLAUDE).executableOnPath()).isFalse();
    assertThat(inspection("").inspect(project, CLAUDE).executableOnPath()).isFalse();
  }

  @Test
  void findsOpenSpecInAnyPathEntryButIgnoresNonExecutableFiles() throws IOException {
    final Path other = Files.createDirectory(project.resolve("other-bin"));
    write(bin.resolve("openspec"));
    executable(other.resolve("openspec"));

    assertThat(inspection(bin.toString()).inspect(project, CLAUDE).executableOnPath()).isFalse();
    assertThat(
            inspection(bin + File.pathSeparator + other)
                .inspect(project, CLAUDE)
                .executableOnPath())
        .isTrue();
  }

  @Test
  void gateOnlyProjectLocalExecutableDoesNotCount() throws IOException {
    executable(project.resolve(".sentinel/tools/openspec/node_modules/.bin/openspec"));

    assertThat(inspection(bin.toString()).inspect(project, CLAUDE).executableOnPath()).isFalse();
  }

  @Test
  void projectMarkerMustBeADirectory() throws IOException {
    write(project.resolve("openspec"));

    assertThat(inspection(bin.toString()).inspect(project, CLAUDE).projectDirectory()).isFalse();
  }

  @Test
  void detectsVerifyAsCommandOrSkillPerAgent() throws IOException {
    write(project.resolve(".opencode/commands/opsx-verify.md"));

    assertThat(inspection(bin.toString()).inspect(project, OPENCODE).verifyWorkflow()).isTrue();
    assertThat(inspection(bin.toString()).inspect(project, CLAUDE).verifyWorkflow()).isFalse();

    write(project.resolve(".claude/commands/opsx/verify.md"));
    assertThat(inspection(bin.toString()).inspect(project, CLAUDE).verifyWorkflow()).isTrue();
  }

  @Test
  void cannotStartProcesses() {
    for (final Constructor<?> constructor :
        FileSystemOpenSpecSetupInspection.class.getConstructors()) {
      assertThat(Arrays.asList(constructor.getParameterTypes()))
          .doesNotContain(CommandExecutor.class);
    }
  }

  private static FileSystemOpenSpecSetupInspection inspection(final String path) {
    return new FileSystemOpenSpecSetupInspection(() -> path);
  }

  private static void write(final Path file) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, "content\n");
  }

  private static void executable(final Path file) throws IOException {
    write(file);
    assertThat(file.toFile().setExecutable(true)).isTrue();
  }
}
