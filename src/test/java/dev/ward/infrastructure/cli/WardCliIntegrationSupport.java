package dev.ward.infrastructure.cli;

import dev.ward.application.agent.IntegrationService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.config.DaggerWardComponent;
import dev.ward.config.WardComponent;
import dev.ward.infrastructure.agent.ClaudeCodeIntegration;
import dev.ward.infrastructure.agent.OpenCodeIntegration;
import dev.ward.infrastructure.cli.agent.IntegrateCommand;
import dev.ward.infrastructure.cli.init.InitCommand;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class WardCliIntegrationSupport {

  protected static final String INIT = "init";
  protected static final String INTEGRATION_OPTION = "--integration";
  protected static final String NONE = "none";
  protected static final String GATE_OPTION = "--gate";
  protected static final String TESTS = "tests";
  protected static final String WARD_TOML = "ward.toml";
  protected static final String CHECK = "check";
  protected static final String FORMAT_OPTION = "--format";
  protected static final String JSON = "json";
  protected static final String STATUS = "status";
  protected static final String CHECKS = "checks";
  protected static final String CLAUDE_CODE = "claude-code";
  protected static final String LEARN_AFTER = "--learn-after";

  protected final WardComponent component = DaggerWardComponent.create();
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
        CommandLineRunnerImpl.createCommandLine(
            (WardCommand) component.commands().get(WardCommand.class).get(),
            factory,
            new PrintWriter(out, true),
            new PrintWriter(err, true));
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
    cli.setExecutionStrategy(CommandLineRunnerImpl.executionStrategy());
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
            CliInitServices.openCodeAndClaude(),
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(init);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    cli.setExecutionStrategy(CommandLineRunnerImpl.executionStrategy());
    return cli.execute(args);
  }
}
