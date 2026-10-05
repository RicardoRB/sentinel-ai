package dev.sentinel.cli;

import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.sentinel.config.SentinelModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** End to end: real Guice wiring, real process execution, against a small Maven project fixture. */
@DisabledOnOs(OS.WINDOWS)
class SentinelCliIntegrationTest {

    private final Injector injector = Guice.createInjector(new SentinelModule());

    @TempDir
    Path project;

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
        Files.setPosixFilePermissions(project.resolve("mvnw"), PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private int run(String... args) {
        CommandLine cli = new CommandLine(injector.getInstance(SentinelCommand.class),
                new GuiceCommandFactory(injector));
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
        return cli.execute(args);
    }

    private int runWithInput(String input, String... args) {
        IntegrateCommand integrate = new IntegrateCommand(
                new dev.sentinel.application.IntegrationService(new dev.sentinel.application.ProjectDetector(),
                        java.util.List.of(new dev.sentinel.application.OpenCodeIntegration(),
                                new dev.sentinel.application.ClaudeCodeIntegration())),
                new ByteArrayInputStream(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        CommandLine cli = new CommandLine(integrate);
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
        return cli.execute(args);
    }

    @Test
    void detectsFixtureProject() {
        assertThat(run("detect", "-C", project.toString())).isZero();
        assertThat(out.toString()).contains("Project detected", "Java", "Maven", "Spring Boot", project.toString());
    }

    @Test
    void detectOnUnsupportedDirectoryExitsNonZero(@TempDir Path empty) {
        assertThat(run("detect", "-C", empty.toString())).isEqualTo(ExitCodes.FAILED);
        assertThat(out.toString().trim()).isEqualTo("No supported project detected.");
    }

    @Test
    void initThenCheckPasses() {
        assertThat(run("init", "-C", project.toString())).isZero();
        assertThat(project.resolve("sentinel.toml")).exists();

        out.getBuffer().setLength(0);
        assertThat(run("check", "-C", project.toString())).isEqualTo(ExitCodes.OK);
        assertThat(out.toString()).contains("Sentinel", "✓ tests", "PASSED", "Quality gate: PASSED");
    }

    @Test
    void initDoesNotOverwriteAndExitsCleanly() throws IOException {
        Files.writeString(project.resolve("sentinel.toml"), "version = 1\n");

        assertThat(run("init", "-C", project.toString())).isZero();

        assertThat(out.toString()).contains("already exists");
        assertThat(project.resolve("sentinel.toml")).hasContent("version = 1\n");
    }

    @Test
    void initThenFailingCheckExitsWithOne() throws IOException {
        run("init", "-C", project.toString());
        Files.createFile(project.resolve("FAIL"));
        out.getBuffer().setLength(0);

        assertThat(run("check", "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
        assertThat(out.toString()).contains("✗ tests", "FAILED", "Quality gate: FAILED", "Command:", "./mvnw test",
                "BUILD FAILURE");
    }

    @Test
    void jsonOutputIsValidJsonAndNothingElse() throws IOException {
        run("init", "-C", project.toString());
        Files.createFile(project.resolve("FAIL"));
        out.getBuffer().setLength(0);

        int exit = run("check", "--format", "json", "-C", project.toString());

        assertThat(exit).isEqualTo(ExitCodes.FAILED);
        JsonNode json = JsonMapper.builder().build().readTree(out.toString()); // fails on any extra text
        assertThat(json.get("status").asString()).isEqualTo("FAILED");
        assertThat(json.get("checks").get(0).get("exitCode").asInt()).isEqualTo(1);
        assertThat(json.get("checks").get(0).get("stdout").asString()).contains("stub mvnw: test");
        assertThat(json.get("checks").get(0).get("stderr").asString()).contains("BUILD FAILURE");
    }

    @Test
    void checkWithoutConfigurationIsAnErrorWithCleanJson() {
        int exit = run("check", "--format", "json", "-C", project.toString());

        assertThat(exit).isEqualTo(ExitCodes.ERROR);
        assertThat(JsonMapper.builder().build().readTree(out.toString()).get("status").asString()).isEqualTo("ERROR");
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
        assertThat(out.toString()).contains("Select an agent integration", "1) opencode", "2) claude-code");
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
}
