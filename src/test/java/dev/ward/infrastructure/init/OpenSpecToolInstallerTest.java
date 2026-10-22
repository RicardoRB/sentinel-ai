package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.ToolInstallationResult;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenSpecToolInstallerTest {
  // npm install --prefix <staging> <package>
  private static final int STAGING_ARGUMENT = 3;
  private static final String NODE = "node";

  @Test
  void installsWithDirectNpmVectorAndPublishesProjectRelativeExecutable(@TempDir final Path root)
      throws IOException {
    final RecordingExecutor executor = new RecordingExecutor();
    final ToolInstallationResult result =
        new OpenSpecToolInstaller(executor).install(root, "openspec");

    assertThat(result.installed()).isTrue();
    assertThat(result.executable())
        .isEqualTo(Path.of(".ward/tools/openspec/node_modules/.bin/openspec"));
    assertThat(executor.commands.get(2))
        .containsExactly(
            "npm",
            "install",
            "--prefix",
            root.resolve(".ward/tools/.openspec-staging").toString(),
            OpenSpecToolInstaller.PACKAGE);
    assertThat(root.resolve("package.json")).doesNotExist();
    assertThat(root.resolve("package-lock.json")).doesNotExist();
  }

  @Test
  void rejectsUnmarkedDestinationWithoutRunningInstaller(@TempDir final Path root)
      throws IOException {
    final Path destination = root.resolve(OpenSpecToolInstaller.DESTINATION);
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("user-file"), "keep");
    final RecordingExecutor executor = new RecordingExecutor();

    final ToolInstallationResult result =
        new OpenSpecToolInstaller(executor).install(root, "openspec");

    assertThat(result.installed()).isFalse();
    assertThat(result.message()).contains("unmarked");
    assertThat(executor.commands).isEmpty();
    assertThat(Files.readString(destination.resolve("user-file"))).isEqualTo("keep");
  }

  private static final class RecordingExecutor implements CommandExecutor {
    private final List<List<String>> commands = new ArrayList<>();

    @Override
    public CommandResult execute(final List<String> command, final Path root) {
      commands.add(List.copyOf(command));
      try {
        if (command.size() > STAGING_ARGUMENT && "npm".equals(command.getFirst())) {
          final Path staging = Path.of(command.get(STAGING_ARGUMENT));
          final Path executable = staging.resolve("node_modules/.bin/openspec");
          Files.createDirectories(executable.getParent());
          Files.writeString(executable, "#!/bin/sh\n");
          executable.toFile().setExecutable(true);
          return result(0, "", "");
        }
      } catch (IOException e) {
        return result(1, "", "");
      }
      final String executable = command.getFirst();
      if (NODE.equals(executable)) {
        return result(0, "v20.19.0\n", "");
      }
      return result(0, "Node.js v20.19.0\n--all --archived", "");
    }

    private static CommandResult result(final int code, final String stdout, final String stderr) {
      return new CommandResult(code, stdout, stderr, Duration.ZERO);
    }
  }
}
