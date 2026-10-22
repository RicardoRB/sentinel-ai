package dev.ward.application.spec;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;

import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

/** Real project files and configuration with a recording executor instead of OpenSpec. */
class SpecValidationTestSupport {
  static final String OPENSPEC = "openspec";
  static final String INVALID =
      "Change 'foo' has issues\n✗ [ERROR] x/spec.md: must include at least one scenario\n"
          + "Next steps:\n  - hint\n";
  static final String BOTH_ENABLED =
      """
      version = 1
      [quality-gates.openspec]
      command = ["openspec", "validate", "--all", "--strict", "--no-interactive"]
      [quality-gates.openspec-archived]
      command = ["openspec", "validate", "--archived", "--no-interactive"]
      """;
  static final List<String> ACTIVE_COMMAND =
      List.of(OPENSPEC, "validate", "--all", "--strict", "--no-interactive");
  static final List<String> ARCHIVED_COMMAND =
      List.of(OPENSPEC, "validate", "--archived", "--no-interactive");

  @TempDir Path root;

  final List<List<String>> commands = new ArrayList<>();
  CommandResult result = new CommandResult(0, "valid", "", Duration.ZERO);

  private final CommandExecutor executor =
      (command, directory) -> {
        commands.add(command);
        return command.contains("--archived")
            ? new CommandResult(1, "", "Change 'old' has incomplete tasks", Duration.ZERO)
            : result;
      };

  SpecValidationService service() {
    return service(inspection(true));
  }

  SpecValidationService service(final EnvironmentInspection environment) {
    return SpecValidationFixtures.service(
        executor, environment, SpecValidationFixtures.checks(executor, environment));
  }

  void project(final String toml, final String... directories) throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(root.resolve("ward.toml"), toml);
    Files.createDirectories(root.resolve(OPENSPEC));
    for (final String directory : directories) {
      Files.createDirectories(root.resolve(directory));
    }
  }

  static EnvironmentInspection inspection(final boolean available) {
    return new FakeOpenSpecEnvironment(available);
  }
}
