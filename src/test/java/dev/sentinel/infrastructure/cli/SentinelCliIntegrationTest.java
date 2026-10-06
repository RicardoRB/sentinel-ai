package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End to end: real Dagger wiring, real process execution, against a small Maven project fixture.
 */
@DisabledOnOs(OS.WINDOWS)
class SentinelCliIntegrationTest {

  private final SentinelComponent component = DaggerSentinelComponent.create();
  private final DaggerCommandFactory factory = new DaggerCommandFactory(component.commands());

  @TempDir Path project;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();

  @BeforeEach
  void copyFixture() throws IOException {
    Path fixture = Path.of("src/test/resources/fixtures/maven-project");
    try (Stream<Path> files = Files.walk(fixture)) {
      for (Path source : files.filter(Files::isRegularFile).toList()) {
        Path target = project.resolve(fixture.relativize(source));
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
      }
    }
    Files.setPosixFilePermissions(
        project.resolve("mvnw"), PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  private int run(String... args) {
    CommandLine cli =
        new CommandLine(component.commands().get(SentinelCommand.class).get(), factory);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }

  private int runWithInput(String input, String... args) {
    IntegrateCommand integrate =
        new IntegrateCommand(
            new IntegrationService(
                new ProjectDetector(new FileSystemProjectInspection()),
                List.of(new OpenCodeIntegration(), new ClaudeCodeIntegration())),
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    CommandLine cli = new CommandLine(integrate);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }

  @Test
  void factoryResolvesEverySentinelSubcommand() throws Exception {
    Class<?>[] subcommands = SentinelCommand.class.getAnnotation(Command.class).subcommands();
    assertThat(subcommands).isNotEmpty();
    for (Class<?> subcommand : subcommands) {
      assertThat(factory.create(subcommand)).isInstanceOf(subcommand);
    }
  }

  private int runInitWithInput(String input, String... args) {
    InitCommand init =
        new InitCommand(
            new InitService(
                new ProjectDetector(new FileSystemProjectInspection()),
                Set.of(new OpenCodeIntegration(), new ClaudeCodeIntegration()),
                new InitSetupCatalog(new SystemEnvironmentInspection()),
                new PomToolConfigurator(),
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()),
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    CommandLine cli = new CommandLine(init);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }

  @Test
  void detectsFixtureProject() {
    assertThat(run("detect", "-C", project.toString())).isZero();
    assertThat(out.toString())
        .contains("Project detected", "Java", "Maven", "Spring Boot", project.toString());
  }

  @Test
  void detectOnUnsupportedDirectoryExitsNonZero(@TempDir Path empty) {
    assertThat(run("detect", "-C", empty.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString().trim()).isEqualTo("No supported project detected.");
  }

  @Test
  void initThenCheckPasses() {
    assertThat(run("init", "--integration", "none", "--gate", "tests", "-C", project.toString()))
        .isZero();
    assertThat(project.resolve("sentinel.toml")).exists();

    out.getBuffer().setLength(0);
    assertThat(run("check", "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).contains("Sentinel", "✓ tests", "PASSED", "Quality gate: PASSED");
  }

  @Test
  void loopCommandUsesProductionCompositionAndRuntimeRequestOptions() {
    assertThat(run("init", "--integration", "none", "--gate", "tests", "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);

    int exit =
        run(
            "loop",
            "complete this task",
            "--agent-command",
            "echo",
            "--max-iterations",
            "1",
            "--timeout-seconds",
            "5",
            "--allow-dirty",
            "-C",
            project.toString());

    assertThat(exit).isZero();
    assertThat(out.toString()).contains("PASSED after 1 iteration(s)");
  }

  @Test
  void initDoesNotOverwriteAndExitsCleanly() throws IOException {
    Files.writeString(project.resolve("sentinel.toml"), "version = 1\n");

    assertThat(run("init", "--integration", "none", "--gate", "tests", "-C", project.toString()))
        .isZero();

    assertThat(out.toString()).contains("already exists");
    assertThat(project.resolve("sentinel.toml")).hasContent("version = 1\n");
  }

  @Test
  void initAsksBeforeOverwritingExistingConfiguration() throws IOException {
    Files.writeString(
        project.resolve("sentinel.toml"),
        "version = 1\n\n[quality-gates.compile]\nenabled = true\ncommand = \"old\"\n");

    assertThat(runInitWithInput("n\n", "-C", project.toString())).isZero();
    assertThat(Files.readString(project.resolve("sentinel.toml"))).contains("command = \"old\"");

    out.getBuffer().setLength(0);
    assertThat(runInitWithInput("y\n1\n1\n", "-C", project.toString())).isZero();
    assertThat(Files.readString(project.resolve("sentinel.toml")))
        .contains("[quality-gates.tests]");
    assertThat(out.toString()).contains("Overwrite it?", "Updated ");
  }

  @Test
  void initThenFailingCheckExitsWithOne() throws IOException {
    run("init", "--integration", "none", "--gate", "tests", "-C", project.toString());
    Files.createFile(project.resolve("FAIL"));
    out.getBuffer().setLength(0);

    assertThat(run("check", "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .contains(
            "✗ tests",
            "FAILED",
            "Quality gate: FAILED",
            "Command:",
            "./mvnw test",
            "BUILD FAILURE");
  }

  @Test
  void jsonOutputIsValidJsonAndNothingElse() throws IOException {
    run("init", "--integration", "none", "--gate", "tests", "-C", project.toString());
    Files.createFile(project.resolve("FAIL"));
    out.getBuffer().setLength(0);

    int exit = run("check", "--format", "json", "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    JsonNode json =
        JsonMapper.builder().build().readTree(out.toString()); // fails on any extra text
    assertThat(json.get("status").asString()).isEqualTo("FAILED");
    assertThat(json.get("checks").get(0).get("exitCode").asInt()).isEqualTo(1);
    assertThat(json.get("checks").get(0).get("stdout").asString()).contains("stub mvnw: test");
    assertThat(json.get("checks").get(0).get("stderr").asString()).contains("BUILD FAILURE");
  }

  @Test
  void checkWithoutConfigurationIsAnErrorWithCleanJson() {
    int exit = run("check", "--format", "json", "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(JsonMapper.builder().build().readTree(out.toString()).get("status").asString())
        .isEqualTo("ERROR");
    assertThat(err.toString()).contains("sentinel init");
  }

  @Test
  void checkOutsideAProjectIsAnError(@TempDir Path empty) {
    assertThat(run("check", "-C", empty.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No supported project detected.");
  }

  @Test
  void invalidFormatIsAUsageError() {
    assertThat(run("check", "--format", "xml")).isEqualTo(ExitCodes.ERROR);
  }

  @Test
  void integratesClaudeCodeAndOpenCodeAndRemovesOwnedArtifacts() {
    assertThat(run("integrate", "claude-code", "-C", project.toString())).isZero();
    assertThat(project.resolve(".claude/settings.json")).exists();
    assertThat(project.resolve(".claude/hooks/sentinel-edit-write")).exists();

    out.getBuffer().setLength(0);
    assertThat(run("integrate", "opencode", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/commands/sentinel-check.md")).exists();
    assertThat(project.resolve(".opencode/plugins/sentinel-edit-write.js")).exists();

    assertThat(run("integrate", "claude-code", "--remove", "-C", project.toString())).isZero();
    assertThat(project.resolve(".claude/settings.json")).doesNotExist();
    assertThat(run("integrate", "opencode", "--remove", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/plugins/sentinel-edit-write.js")).doesNotExist();
  }

  @Test
  void unknownIntegrationListsSupportedAgentsWithoutWritingFiles() {
    assertThat(run("integrate", "unknown", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Supported agents: opencode, claude-code");
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void promptsForAgentWhenArgumentIsOmitted() {
    assertThat(runWithInput("2\n", "-C", project.toString())).isZero();
    assertThat(out.toString())
        .contains("Select an agent integration", "1) opencode", "2) claude-code");
    assertThat(project.resolve(".claude/settings.json")).exists();
  }

  @Test
  void rejectsInvalidInteractiveSelectionWithoutWritingFiles() {
    assertThat(runWithInput("9\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid agent selection");
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void rejectsEndOfInputWithoutWritingFiles() {
    assertThat(runWithInput("", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No agent selected");
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void initPromptsForIntegrationAndGateAndCreatesSelectedConfiguration() throws IOException {
    assertThat(runInitWithInput("1\n1\n", "-C", project.toString())).isZero();
    assertThat(out.toString())
        .contains("Select agent integrations", "> [ ] 1)", "Select quality gates", "AVAILABLE");
    assertThat(Files.readString(project.resolve("sentinel.toml")))
        .contains("[quality-gates.tests]");
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void initFallsBackToNumberedPromptsWhenRawTerminalIsUnavailable() throws IOException {
    assertThat(runInitWithInput("1\n1\n", "-C", project.toString())).isZero();

    assertThat(out.toString())
        .contains(
            "enter numbers separated by spaces", "Toggle integrations", "Toggle quality gates");
    assertThat(project.resolve("sentinel.toml")).exists();
  }

  @Test
  void initInstallsMultipleIntegrationsSelectedWithSpaces() {
    assertThat(runInitWithInput("2 3\n1\n", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/commands/sentinel-check.md")).exists();
    assertThat(project.resolve(".opencode/plugins/sentinel-edit-write.js")).exists();
    assertThat(project.resolve(".claude/settings.json")).exists();
    assertThat(out.toString())
        .contains("Created Sentinel-owned OpenCode", "Created Sentinel-owned Claude Code");
  }

  @Test
  void initConfiguresMultipleQualityGatesSelectedWithSpaces() throws IOException {
    assertThat(runInitWithInput("1\n1 2\n", "-C", project.toString())).isZero();
    String config = Files.readString(project.resolve("sentinel.toml"));
    assertThat(config).contains("[quality-gates.tests]", "[quality-gates.compile]");
  }

  @Test
  void archunitSelectionPromptsForArchitectureAndGeneratesTest() throws IOException {
    assertThat(runInitWithInput("1\n8\n2\n", "-C", project.toString())).isZero();
    Path test = project.resolve("src/test/java/com/example/ArchitectureTest.java");
    assertThat(test).exists();
    assertThat(Files.readString(test))
        .contains("hexagonal", "@AnalyzeClasses(packages = \"com.example\")");
    assertThat(Files.readString(project.resolve("pom.xml"))).contains("archunit-junit5");
    assertThat(out.toString())
        .contains("Select an architecture style", "Generated hexagonal ArchUnit test");
  }

  @Test
  void archunitGateTargetsOnlyGeneratedArchitectureTest() throws IOException {
    assertThat(
            run(
                "init",
                "--integration",
                "none",
                "--gate",
                "archunit",
                "--architecture",
                "layered",
                "-C",
                project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve("sentinel.toml")))
        .contains("./mvnw -Dtest=ArchitectureTest test");
  }

  @Test
  void existingArchitectureTestIsPreserved() throws IOException {
    Path test = project.resolve("src/test/java/com/example/ArchitectureTest.java");
    Files.createDirectories(test.getParent());
    Files.writeString(test, "user-owned architecture test\n");

    assertThat(runInitWithInput("1\n8\n1\n", "-C", project.toString())).isZero();
    assertThat(test).hasContent("user-owned architecture test\n");
    assertThat(out.toString()).contains("Preserved existing ArchUnit test");
  }

  @Test
  void selectedCheckstyleAddsMissingMavenPluginAndReportsIt() throws IOException {
    assertThat(
            run("init", "--integration", "none", "--gate", "checkstyle", "-C", project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve("pom.xml"))).contains("maven-checkstyle-plugin");
    assertThat(out.toString())
        .contains("Updated pom.xml with Maven tools", "maven-checkstyle-plugin");
  }

  @Test
  void existingMavenPluginIsNotDuplicated() throws IOException {
    Path pom = project.resolve("pom.xml");
    String original = Files.readString(pom);
    Files.writeString(
        pom,
        original.replace(
            "</project>",
            """
                <build><plugins><plugin><artifactId>maven-checkstyle-plugin</artifactId></plugin></plugins></build>
                </project>"""));

    assertThat(
            run("init", "--integration", "none", "--gate", "checkstyle", "-C", project.toString()))
        .isZero();
    String updated = Files.readString(pom);
    assertThat(updated.indexOf("<artifactId>maven-checkstyle-plugin</artifactId>"))
        .isEqualTo(updated.lastIndexOf("<artifactId>maven-checkstyle-plugin</artifactId>"));
    assertThat(out.toString()).doesNotContain("Updated pom.xml with Maven tools");
  }

  @Test
  void generatedMavenAndArchitectureFilesRollBackOnLaterSetupFailure() throws IOException {
    Path testRoot = project.resolve("src/test/java");
    Files.createDirectories(testRoot.getParent());
    Files.writeString(testRoot, "not a directory\n");
    String originalPom = Files.readString(project.resolve("pom.xml"));

    assertThat(
            run(
                "init",
                "--integration",
                "none",
                "--gate",
                "archunit",
                "--architecture",
                "clean",
                "-C",
                project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(Files.readString(project.resolve("pom.xml"))).isEqualTo(originalPom);
    assertThat(project.resolve("sentinel.toml")).doesNotExist();
  }

  @Test
  void rejectsCombiningNoIntegrationWithAnAgent() {
    assertThat(runInitWithInput("1 2\n1\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("no-integration choice cannot be combined");
    assertThat(project.resolve("sentinel.toml")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void rollsBackEarlierIntegrationWhenLaterSelectionConflicts() throws IOException {
    Path target = project.resolve(".opencode/commands/sentinel-check.md");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned\n");

    assertThat(runInitWithInput("3 2\n1\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve("sentinel.toml")).doesNotExist();
    assertThat(target).hasContent("user-owned\n");
  }

  @Test
  void initReportsIntegrationConflictWithoutCreatingConfiguration() throws IOException {
    Path target = project.resolve(".opencode/commands/sentinel-check.md");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned\n");

    assertThat(
            run("init", "--integration", "opencode", "--gate", "tests", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("selected integration conflicts");
    assertThat(project.resolve("sentinel.toml")).doesNotExist();
    assertThat(target).hasContent("user-owned\n");
  }

  @Test
  void initRejectsInvalidAndEmptySelectionsWithoutWriting() {
    assertThat(runInitWithInput("x\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid integration selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("1\nx\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid quality-gate selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("1\n\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Select at least one quality gate");
    assertThat(project.resolve("sentinel.toml")).doesNotExist();
  }

  @Test
  void initRejectsInvalidArchitectureAndEof() {
    assertThat(runInitWithInput("1\n8\nnope\n", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid architecture selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("1\n8\n9\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid architecture selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("1\n8\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("input ended");
  }

  @Test
  void checkSupportsProfilesAndStrictJson() throws IOException {
    run("init", "--integration", "none", "--gate", "tests,compile", "-C", project.toString());
    Files.writeString(
        project.resolve("sentinel.toml"),
        """
                version = 1
                 [quality-gates.tests]
                 command = "./mvnw test"
                 profiles = ["strict", "fast"]
                 [quality-gates.compile]
                 command = "./mvnw compile"
                 profiles = ["strict"]
                """);
    out.getBuffer().setLength(0);
    assertThat(run("check", "--profile", "strict", "--format", "json", "-C", project.toString()))
        .isZero();
    assertThat(out.toString()).contains("\"policies\"", "\"status\" : \"PASSED\"");
    out.getBuffer().setLength(0);
    assertThat(
            run("check", "--profile", "fast,strict", "--format", "json", "-C", project.toString()))
        .isZero();
    assertThat(out.toString()).contains("\"policies\"");
    out.getBuffer().setLength(0);
    assertThat(run("check", "--profile", "fast", "--profile", "strict", "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);
    assertThat(run("check", "--format", "json", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("\"status\" : \"ERROR\"").doesNotContain("Unknown");
    out.getBuffer().setLength(0);
    assertThat(run("check", "--profile", "unknown", "--format", "json", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("\"status\" : \"ERROR\"");
  }

  @Test
  void doctorReportsConfigurationAndIntegrationStates() throws IOException {
    assertThat(run("doctor", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("configuration", "ERROR", "integration", "WARNING");
    out.getBuffer().setLength(0);
    run("init", "--integration", "opencode", "--gate", "tests", "-C", project.toString());
    assertThat(run("doctor", "-C", project.toString())).isZero();
    assertThat(out.toString()).contains("configuration", "OK", "integration", "OK");
  }

  @Test
  void directIntegrationHandlesNotFoundAndRemoval() {
    assertThat(run("integrate", "opencode", "--remove", "-C", project.toString())).isZero();
    assertThat(out.toString()).contains("NOT_FOUND");
    out.getBuffer().setLength(0);
    assertThat(run("integrate", "claude-code", "--remove", "-C", project.toString())).isZero();
    assertThat(out.toString()).contains("NOT_FOUND");
  }

  @Test
  void interactiveIntegrationRejectsInvalidAndEof() {
    assertThat(runWithInput("9\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid agent selection");
    err.getBuffer().setLength(0);
    assertThat(runWithInput("", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No agent selected");
  }
}
