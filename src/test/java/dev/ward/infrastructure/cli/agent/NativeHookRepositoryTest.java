package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.agent.HookScope;
import dev.ward.application.project.ProjectDetector;
import dev.ward.application.spec.ApprovedArtifactGuardService;
import dev.ward.application.spec.SpecValidationFixtures;
import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.agent.CodexIntegration;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.spec.FileSystemProjectPaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** A native hook started at a monorepo root checks the configured projects its event touches. */
class NativeHookRepositoryTest {
  private static final String STOP = "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}";
  private static final String TOML = "version = 1\n[quality-gates.tests]\ncommand = \"mvn test\"\n";

  @TempDir Path repository;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();
  private final List<Path> ranIn = new ArrayList<>();
  private Path api;
  private Path billing;

  @BeforeEach
  void monorepo() throws IOException {
    Files.createDirectories(repository.resolve(".git"));
    api = project("apps/api");
    billing = project("services/billing");
    Files.createDirectories(repository.resolve("apps/web"));
  }

  private Path project(final String path) throws IOException {
    final Path root = Files.createDirectories(repository.resolve(path));
    withPom(root, PLAIN_POM);
    Files.writeString(root.resolve("ward.toml"), TOML);
    return root;
  }

  @Test
  void editInOneProjectChecksOnlyThatProject() {
    assertThat(run(1, patch("apps/api/src/App.java"))).isNotZero();

    assertThat(ranIn).isNotEmpty().allMatch(api::equals);
    assertThat(err.toString()).contains("Ward checked " + Path.of("apps/api") + ":");
  }

  @Test
  void editOutsideEveryProjectChecksNothing() {
    assertThat(run(1, patch("apps/web/page.tsx"))).isZero();

    assertThat(ranIn).isEmpty();
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void projectWithoutWardConfigurationIsNotChecked() throws IOException {
    Files.delete(billing.resolve("ward.toml"));

    assertThat(run(1, patch("services/billing/src/Pay.java"))).as(err.toString()).isZero();

    assertThat(ranIn).isEmpty();
  }

  @Test
  void turnEndChecksEveryChangedProject() {
    assertThat(run(0, STOP)).isZero();

    assertThat(ranIn).contains(api, billing);
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void guardOutsideEveryProjectWithoutSharedSpecsAllowsTheCall() {
    assertThat(run(0, patch("apps/api/src/App.java"), "--guard-approved-specs")).isZero();

    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void guardProtectsTheSharedOpenSpecDirectory() throws IOException {
    Files.createDirectories(repository.resolve("openspec/specs/auth"));
    Files.writeString(repository.resolve("openspec/specs/auth/spec.md"), "# Auth\n");

    run(0, patch("openspec/specs/auth/spec.md"), "--guard-approved-specs");

    assertThat(out.toString()).contains("deny");
  }

  private static String patch(final String path) {
    return "{\"hook_event_name\":\"PostToolUse\",\"tool_name\":\"apply_patch\",\"cwd\":\"\","
        + "\"tool_input\":{\"command\":\"*** Begin Patch\\n*** Update File: "
        + path
        + "\\n@@\\n-a\\n+b\\n*** End Patch\"}}";
  }

  private int run(final int gateExit, final String payload, final String... flags) {
    final CommandExecutor executor =
        (command, directory) -> {
          ranIn.add(directory);
          return new CommandResult(gateExit, "", "", Duration.ZERO);
        };
    final FakeOpenSpecEnvironment environment = new FakeOpenSpecEnvironment(true);
    final ProjectDetector detector = new ProjectDetector(new FileSystemProjectInspection());
    final NativeHookCommand command =
        new NativeHookCommand(
            new HookChecks(
                SpecValidationFixtures.checks(executor, environment),
                SpecValidationFixtures.service(
                    executor, environment, SpecValidationFixtures.checks(executor, environment)),
                new HookScope(detector, directory -> true, new FileSystemProjectPaths())),
            new ApprovedArtifactGuardService(detector, new FileSystemProjectPaths()),
            new ForyJsonCodec(),
            Set.of(new CodexIntegration()),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    final List<String> args =
        new ArrayList<>(List.of("--agent", "codex", "--event-stdin", "-C", repository.toString()));
    args.addAll(List.of(flags));
    return cli.execute(args.toArray(String[]::new));
  }
}
