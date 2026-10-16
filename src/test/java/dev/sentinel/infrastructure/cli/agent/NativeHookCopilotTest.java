package dev.sentinel.infrastructure.cli.agent;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.application.spec.SpecValidationFixtures;
import dev.sentinel.domain.FakeOpenSpecEnvironment;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.infrastructure.agent.CopilotIntegration;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Pins Copilot CLI feedback delivery: post-edit findings travel as additionalContext. */
class NativeHookCopilotTest {
  @TempDir Path root;

  private final StringWriter out = new StringWriter();
  private final StringWriter err = new StringWriter();

  @BeforeEach
  void project() throws IOException {
    withPom(root, PLAIN_POM);
    Files.writeString(
        root.resolve("sentinel.toml"),
        "version = 1\n[quality-gates.tests]\ncommand = \"mvn test\"\n");
    Files.createDirectories(root.resolve("openspec/specs/auth"));
    Files.writeString(root.resolve("openspec/specs/auth/spec.md"), "# Auth\n");
  }

  private int run(final int gateExit, final String payload, final String... flags) {
    final CommandExecutor executor =
        (command, directory) -> new CommandResult(gateExit, "", "", Duration.ZERO);
    final FakeOpenSpecEnvironment environment = new FakeOpenSpecEnvironment(true);
    final NativeHookCommand command =
        new NativeHookCommand(
            SpecValidationFixtures.checks(executor, environment),
            SpecValidationFixtures.service(
                executor, environment, SpecValidationFixtures.checks(executor, environment)),
            new ApprovedArtifactGuardService(
                new ProjectDetector(new FileSystemProjectInspection()),
                new FileSystemProjectPaths()),
            new ForyJsonCodec(),
            Set.of(new CopilotIntegration()),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    final String[] base = {"--agent", "copilot", "--event-stdin", "-C", root.toString()};
    final String[] args = new String[base.length + flags.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(flags, 0, args, base.length, flags.length);
    return cli.execute(args);
  }

  private String edit(final String event, final String path) {
    return "{\"hook_event_name\":\""
        + event
        + "\",\"session_id\":\"s\",\"timestamp\":\"t\",\"cwd\":\""
        + root
        + "\",\"tool_name\":\"edit\",\"tool_input\":{\"path\":\""
        + path
        + "\",\"old_str\":\"a\",\"new_str\":\"b\"}}";
  }

  @Test
  void failingChecksAfterAnEditReachTheAgentAsAdditionalContext() {
    assertThat(run(1, edit("PostToolUse", root.resolve("src/A.java").toString()))).isZero();
    assertThat(out.toString()).startsWith("{\"additionalContext\":");
    assertThat(out.toString()).contains("Sentinel quality findings");
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void aCleanEditIsSilent() {
    assertThat(run(0, edit("PostToolUse", root.resolve("src/A.java").toString()))).isZero();
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void failingChecksAtStopContinueTheTurnOnce() {
    assertThat(run(1, "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}")).isZero();
    assertThat(out.toString()).startsWith("{\"decision\":\"block\",\"reason\":");
    out.getBuffer().setLength(0);
    assertThat(run(1, "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":true}")).isZero();
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).contains("Sentinel quality findings");
  }

  @Test
  void theGuardDeniesAnAbsoluteEditPathInsideApprovedSpecs() {
    final String spec = root.resolve("openspec/specs/auth/spec.md").toString();
    assertThat(run(0, edit("PreToolUse", spec), "--guard-approved-specs")).isZero();
    assertThat(out.toString())
        .startsWith("{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":")
        .contains("openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardDeniesAProtectedShellCommand() {
    assertThat(
            run(
                0,
                "{\"hook_event_name\":\"PreToolUse\",\"cwd\":\""
                    + root
                    + "\",\"tool_name\":\"bash\",\"tool_input\":{\"command\":\"rm openspec/specs/auth/spec.md\"}}",
                "--guard-approved-specs"))
        .isZero();
    assertThat(out.toString()).contains("\"permissionDecision\":\"deny\"");
  }

  @Test
  void theGuardAllowsAnEditOutsideApprovedSpecs() {
    assertThat(
            run(
                0,
                edit("PreToolUse", root.resolve("src/A.java").toString()),
                "--guard-approved-specs"))
        .isZero();
    assertThat(out.toString()).isEmpty();
  }
}
