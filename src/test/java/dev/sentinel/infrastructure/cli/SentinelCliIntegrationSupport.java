package dev.sentinel.infrastructure.cli;

import dev.sentinel.application.agent.IntegrationService;
import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.config.DaggerSentinelComponent;
import dev.sentinel.config.SentinelComponent;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import dev.sentinel.infrastructure.cli.agent.IntegrateCommand;
import dev.sentinel.infrastructure.cli.init.InitCommand;
import dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class SentinelCliIntegrationSupport {

  protected static final String INIT = "init";
  protected static final String INTEGRATION_OPTION = "--integration";
  protected static final String NONE = "none";
  protected static final String GATE_OPTION = "--gate";
  protected static final String TESTS = "tests";
  protected static final String SENTINEL_TOML = "sentinel.toml";
  protected static final String CHECK = "check";
  protected static final String FORMAT_OPTION = "--format";
  protected static final String JSON = "json";
  protected static final String STATUS = "status";
  protected static final String CHECKS = "checks";
  protected static final String CLAUDE_CODE = "claude-code";
  protected static final String LEARN_AFTER = "--learn-after";

  protected final SentinelComponent component = DaggerSentinelComponent.create();
  protected final DaggerCommandFactory factory = new DaggerCommandFactory(component.commands());

  @TempDir Path project;

  protected final StringWriter out = new StringWriter();
  protected final StringWriter err = new StringWriter();

  @BeforeEach
  void copyFixture() throws IOException {
    final Path fixture = Path.of("src/test/resources/fixtures/maven-project");
    try (Stream<Path> files = Files.walk(fixture)) {
      for (final Path source : files.filter(Files::isRegularFile).toList()) {
        final Path target = project.resolve(fixture.relativize(source));
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
      }
    }
    Files.setPosixFilePermissions(
        project.resolve("mvnw"), PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  protected int run(final String... args) {
    final CommandLine cli =
        new CommandLine(component.commands().get(SentinelCommand.class).get(), factory);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }

  protected int runWithInput(final String input, final String... args) {
    final IntegrateCommand integrate =
        new IntegrateCommand(
            new IntegrationService(
                new ProjectDetector(new FileSystemProjectInspection()),
                List.of(new OpenCodeIntegration(), new ClaudeCodeIntegration())),
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(integrate);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }

  protected void fail() throws IOException {
    Files.createFile(project.resolve("FAIL"));
  }

  protected void pass() throws IOException {
    Files.deleteIfExists(project.resolve("FAIL"));
  }

  protected int runInitWithInput(final String input, final String... args) {
    final InitCommand init =
        new InitCommand(
            new InitService(
                new ProjectDetector(new FileSystemProjectInspection()),
                Set.of(new OpenCodeIntegration(), new ClaudeCodeIntegration()),
                new InitSetupCatalog(new SystemEnvironmentInspection()),
                new PomToolConfigurator(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()),
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(init);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }
}
