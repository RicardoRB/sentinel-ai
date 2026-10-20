package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitProjectFiles;
import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.OwnedSectionWriter;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.init.QualityHookInstaller;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
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
    // Mirrors the production handler: Sentinel errors print their message and exit with 2.
    command.setExecutionExceptionHandler(
        (exception, commandLine, parseResult) -> {
          commandLine.getErr().println(exception.getMessage());
          return exception instanceof SentinelException ? ExitCodes.ERROR : 1;
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
    return Files.readString(root.resolve("sentinel.toml"));
  }
}
