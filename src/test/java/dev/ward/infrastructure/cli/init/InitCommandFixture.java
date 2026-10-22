package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitProjectFiles;
import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.infrastructure.agent.ClaudeCodeIntegration;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.init.FileConfigurationStorage;
import dev.ward.infrastructure.init.OwnedSectionWriter;
import dev.ward.infrastructure.init.PomToolConfigurator;
import dev.ward.infrastructure.init.PresetRuleFileWriter;
import dev.ward.infrastructure.init.QualityHookInstaller;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import picocli.CommandLine;

/** Runs {@code init} against a temporary Maven project with real adapters and chosen input. */
final class InitCommandFixture {
  private final Path root;
  private final BuildToolConfiguration buildTools;
  private final String[] executables;
  private final StringWriter output = new StringWriter();

  InitCommandFixture(final Path root) throws IOException {
    this(root, new PomToolConfigurator());
  }

  InitCommandFixture(final Path root, final BuildToolConfiguration buildTools) throws IOException {
    this(root, buildTools, new String[0]);
  }

  /** {@code executables} are the commands the fake environment reports on the PATH. */
  InitCommandFixture(
      final Path root, final BuildToolConfiguration buildTools, final String... executables)
      throws IOException {
    this.root = root;
    this.buildTools = buildTools;
    this.executables = executables.clone();
    if (!Files.exists(root.resolve("pom.xml"))) {
      Files.writeString(
          root.resolve("pom.xml"),
          "<project><modelVersion>4.0.0</modelVersion><groupId>example</groupId>"
              + "<artifactId>demo</artifactId><version>1.0.0</version></project>");
    }
    Files.createDirectories(root.resolve(".git/hooks"));
  }

  /** Runs init with piped input. */
  int run(final String input, final String... args) {
    return run(false, input, args);
  }

  /** Runs init; {@code terminal} makes the input behave like an interactive terminal. */
  int run(final boolean terminal, final String input, final String... args) {
    final InitService service =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(new ClaudeCodeIntegration()),
            new InitSetupCatalog(
                new FakeEnvironmentInspection(executables),
                new QualityHookInstaller(),
                new TomlConfigurationReader(),
                "1.2.3"),
            new InitProjectFiles(
                buildTools,
                new PresetRuleFileWriter(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage(),
                new OwnedSectionWriter()));
    final CommandLine command =
        new CommandLine(
            new InitCommand(
                service, new TerminalLikeInputStream(input), Optional::empty, terminal));
    command.setOut(new PrintWriter(output, true));
    command.setErr(new PrintWriter(output, true));
    // Mirrors the production handler: Ward errors print their message and exit with 2.
    command.setExecutionExceptionHandler(
        (exception, commandLine, parseResult) -> {
          commandLine.getErr().println(exception.getMessage());
          return exception instanceof WardException ? ExitCodes.ERROR : 1;
        });
    final String[] withDirectory = new String[args.length + 2];
    withDirectory[0] = "--directory";
    withDirectory[1] = root.toString();
    System.arraycopy(args, 0, withDirectory, 2, args.length);
    return command.execute(withDirectory);
  }

  String output() {
    return output.toString();
  }

  String configuration() throws IOException {
    return Files.readString(root.resolve("ward.toml"));
  }
}
