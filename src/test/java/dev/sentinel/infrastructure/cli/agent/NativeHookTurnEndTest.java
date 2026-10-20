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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Pins turn-end delivery and Codex payload handling for the native hook. */
class NativeHookTurnEndTest {
  private static final String STOP = "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}";
  private static final String STOP_CONTINUED =
      "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":true}";

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
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    final String[] base = {"--agent", "codex", "--event-stdin", "-C", root.toString()};
    final String[] args = new String[base.length + flags.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(flags, 0, args, base.length, flags.length);
    return cli.execute(args);
  }

  @Test
  void failingChecksAtTurnEndContinueTheTurnThroughTheNativeEnvelope() {
    assertThat(run(1, STOP)).isZero();
    assertThat(out.toString()).startsWith("{\"decision\":\"block\",\"reason\":");
    assertThat(out.toString()).contains("Sentinel quality findings");
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void aTurnAlreadyContinuedByTheHookIsNotBlockedAgain() {
    assertThat(run(1, STOP_CONTINUED)).isZero();
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).contains("Sentinel quality findings");
  }

  @Test
  void aCleanTurnEndIsSilent() {
    assertThat(run(0, STOP)).isZero();
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void failingChecksAfterAToolCallExitTwoWithFeedbackOnStderr() {
    assertThat(
            run(
                1,
                "{\"hook_event_name\":\"PostToolUse\",\"tool_name\":\"apply_patch\","
                    + "\"tool_input\":{\"command\":\"*** Begin Patch\"}}"))
        .isEqualTo(2);
    assertThat(err.toString()).contains("Sentinel quality findings");
  }

  @Test
  void codexPatchToolInputReachesTheGuard() {
    final String patch =
        "*** Begin Patch\\n*** Update File: openspec/specs/auth/spec.md\\n@@\\n-# Auth\\n+# Changed\\n*** End Patch";
    assertThat(
            run(
                0,
                "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"apply_patch\","
                    + "\"tool_input\":{\"command\":\""
                    + patch
                    + "\"}}",
                "--guard-approved-specs"))
        .isZero();
    assertThat(out.toString())
        .contains("\"permissionDecision\":\"deny\"")
        .doesNotContain("could not inspect");
  }

  @Test
  void codexShellToolInputReachesTheGuard() {
    assertThat(
            run(
                0,
                "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Bash\","
                    + "\"tool_input\":{\"command\":\"rm openspec/specs/auth/spec.md\"}}",
                "--guard-approved-specs"))
        .isZero();
    assertThat(out.toString())
        .contains("\"permissionDecision\":\"deny\"")
        .doesNotContain("could not inspect");
  }

  @Test
  void aHarmlessCodexShellCommandIsAllowed() {
    assertThat(
            run(
                0,
                "{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Bash\","
                    + "\"tool_input\":{\"command\":\"ls src\"}}",
                "--guard-approved-specs"))
        .isZero();
    assertThat(out.toString()).isEmpty();
  }
}
