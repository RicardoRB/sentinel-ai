package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;

import dev.ward.application.gate.CheckService;
import dev.ward.application.learning.LearningService;
import dev.ward.application.project.ProjectDetector;
import dev.ward.application.spec.ApprovedArtifactGuardService;
import dev.ward.application.spec.SpecValidationFixtures;
import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.json.ForyJsonCodec;
import dev.ward.infrastructure.learning.JsonLearningStore;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.spec.FileSystemProjectPaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Builds the hook with real configuration and project files and a recording executor. */
class ClaudeCodeHookTestSupport {
  static final String CONFIG =
      """
      version = 1
      [quality-gates.tests]
      command = "mvn test"
      [quality-gates.openspec]
      command = ["openspec", "validate", "--all", "--strict", "--no-interactive"]
      """;
  static final String INVALID =
      "Change 'foo' has issues\n✗ [ERROR] x/spec.md: must include at least one scenario\n";

  @TempDir Path root;

  final StringWriter out = new StringWriter();
  final StringWriter err = new StringWriter();
  final List<List<String>> commands = new ArrayList<>();
  boolean testsPass = true;
  boolean specsValid = true;

  private final CommandExecutor executor =
      (command, directory) -> {
        commands.add(command);
        final boolean openspec = "openspec".equals(command.getFirst());
        if (!openspec) {
          return new CommandResult(
              testsPass ? 0 : 1, "", testsPass ? "" : "1 test failed", Duration.ZERO);
        }
        return specsValid
            ? new CommandResult(0, "valid", "", Duration.ZERO)
            : new CommandResult(1, "", INVALID, Duration.ZERO);
      };

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(root.resolve("ward.toml"), CONFIG);
    Files.createDirectories(root.resolve("openspec/changes/foo"));
  }

  int hook(final String payload) {
    out.getBuffer().setLength(0);
    return run(
        new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
        false,
        "--learn-after",
        "1",
        "--exclude-spec",
        "--event-stdin",
        "-C",
        root.toString());
  }

  int run(final InputStream input, final boolean interactive, final String... args) {
    final ForyJsonCodec codec = new ForyJsonCodec();
    final EnvironmentInspection environment = new FakeOpenSpecEnvironment(true);
    final CheckService checks = SpecValidationFixtures.checks(executor, environment);
    final ClaudeCodeHookCommand command =
        new ClaudeCodeHookCommand(
            new HookChecks(checks, SpecValidationFixtures.service(executor, environment, checks)),
            new LearningService(new JsonLearningStore(codec)),
            codec,
            new ClaudeGuardHook(
                new ApprovedArtifactGuardService(
                    new ProjectDetector(new FileSystemProjectInspection()),
                    new FileSystemProjectPaths()),
                codec),
            new HookStdin(input, () -> interactive));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    return cli.execute(args);
  }

  String postToolUse(final String path) {
    return """
        {"hook_event_name":"PostToolUse","tool_name":"Edit","tool_input":{"file_path":"%s"}}
        """
        .formatted(path);
  }

  static String stop(final boolean active) {
    return """
        {"hook_event_name":"Stop","stop_hook_active":%s,"last_assistant_message":"done",
         "background_tasks":[],"session_crons":[]}
        """
        .formatted(active);
  }

  static InputStream failingInput() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("stdin must not be read");
      }
    };
  }
}
