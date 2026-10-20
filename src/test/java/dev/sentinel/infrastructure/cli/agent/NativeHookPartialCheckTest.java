package dev.sentinel.infrastructure.cli.agent;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.application.spec.SpecValidationFixtures;
import dev.sentinel.domain.FakeOpenSpecEnvironment;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.infrastructure.agent.CodexIntegration;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.infrastructure.spec.FileSystemProjectPaths;
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

/** Native harnesses check the edited files after an edit and the whole project at turn end. */
class NativeHookPartialCheckTest {
  private static final List<String> TESTS = List.of("mvn", "test");
  private static final List<String> CHECKSTYLE = List.of("mvn", "checkstyle:check");
  private static final String PATCH =
      "*** Begin Patch\\n"
          + "*** Update File: src/A.java\\n@@\\n-a\\n+b\\n"
          + "*** Add File: docs/b.md\\n+# B\\n"
          + "*** Delete File: src/Old.java\\n"
          + "*** Update File: src/M.java\\n*** Move to: src/N.java\\n@@\\n-m\\n+n\\n"
          + "*** End Patch";

  @TempDir Path root;

  private final List<List<String>> commands = new ArrayList<>();

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(
        root.resolve("sentinel.toml"),
        """
        version = 1
        [quality-gates.tests]
        command = "mvn test"
        [quality-gates.checkstyle]
        command = "mvn checkstyle:check"
        [quality-gates.checkstyle.partial]
        inputs = ["src/**/*.java"]
        args = ["-Dcheckstyle.includes={paths}"]
        item = "**/{name}"
        separator = ","
        """);
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/A.java"), "class A {}\n");
    Files.writeString(root.resolve("src/N.java"), "class N {}\n");
  }

  @Test
  void multiFilePatchNarrowsToTheFilesThatStillExist() {
    assertThat(run(postPatch(PATCH))).isZero();

    assertThat(commands)
        .containsExactly(
            TESTS, List.of("mvn", "checkstyle:check", "-Dcheckstyle.includes=**/A.java,**/N.java"));
  }

  @Test
  void markdownOnlyPatchSkipsTheJavaGate() {
    assertThat(run(postPatch("*** Begin Patch\\n*** Add File: docs/b.md\\n+# B\\n*** End Patch")))
        .isZero();

    assertThat(commands).containsExactly(TESTS);
  }

  @Test
  void eventWithoutPathsRunsEveryGateInFull() {
    assertThat(run(postPatch("*** Begin Patch"))).isZero();

    assertThat(commands).containsExactly(TESTS, CHECKSTYLE);
  }

  @Test
  void turnEndRunsEveryGateInFull() {
    assertThat(run("{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}")).isZero();

    assertThat(commands).containsExactly(TESTS, CHECKSTYLE);
  }

  @Test
  void editedPathsComeFromTheEventPathOrEveryPatchTarget() {
    final Path directory = root.resolve("module");

    assertThat(NativeHookEditedPaths.of(event("src/A.java", null), directory))
        .containsExactly(directory.toAbsolutePath().resolve("src/A.java"));
    assertThat(
            NativeHookEditedPaths.of(
                event(null, PATCH.replace("\\n", "\n")), directory.toAbsolutePath()))
        .extracting(path -> directory.toAbsolutePath().relativize(path).toString())
        .containsExactly("src/A.java", "docs/b.md", "src/Old.java", "src/M.java", "src/N.java");
    assertThat(NativeHookEditedPaths.of(event(" ", null), directory)).isEmpty();
  }

  private static NativeHookInput event(final String path, final String patch) {
    return new NativeHookInput(
        "PostToolUse", "apply_patch", null, path, null, patch, null, false, false);
  }

  private static String postPatch(final String patch) {
    return "{\"hook_event_name\":\"PostToolUse\",\"tool_name\":\"apply_patch\","
        + "\"tool_input\":{\"command\":\""
        + patch
        + "\"}}";
  }

  private int run(final String payload) {
    final CommandExecutor executor =
        (command, directory) -> {
          commands.add(command);
          return new CommandResult(0, "", "", Duration.ZERO);
        };
    final FakeOpenSpecEnvironment environment = new FakeOpenSpecEnvironment(true);
    final NativeHookCommand command =
        new NativeHookCommand(
            new HookChecks(
                SpecValidationFixtures.checks(executor, environment),
                SpecValidationFixtures.service(
                    executor, environment, SpecValidationFixtures.checks(executor, environment))),
            new ApprovedArtifactGuardService(
                new ProjectDetector(new FileSystemProjectInspection()),
                new FileSystemProjectPaths()),
            new ForyJsonCodec(),
            Set.of(new CodexIntegration()),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(new StringWriter(), true));
    cli.setErr(new PrintWriter(new StringWriter(), true));
    return cli.execute("--agent", "codex", "--event-stdin", "-C", root.toString());
  }
}
