package dev.ward.infrastructure.cli.agent;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;

import dev.ward.application.project.ProjectDetector;
import dev.ward.application.spec.ApprovedArtifactGuardService;
import dev.ward.application.spec.SpecValidationFixtures;
import dev.ward.domain.FakeOpenSpecEnvironment;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.infrastructure.agent.NativeHarnessAdapter;
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
import java.util.Set;
import picocli.CommandLine;

/** Runs {@code hook native} for one adapter in a project with an approved spec. */
final class NativeHookRun {
  final StringWriter out = new StringWriter();
  final StringWriter err = new StringWriter();
  private final Path root;
  private final NativeHarnessAdapter adapter;

  NativeHookRun(final Path root, final NativeHarnessAdapter adapter) throws IOException {
    this.root = root;
    this.adapter = adapter;
    withPom(root, PLAIN_POM);
    Files.writeString(
        root.resolve("ward.toml"), "version = 1\n[quality-gates.tests]\ncommand = \"mvn test\"\n");
    Files.createDirectories(root.resolve("openspec/specs/auth"));
    Files.writeString(root.resolve("openspec/specs/auth/spec.md"), "# Auth\n");
  }

  int run(final int gateExit, final String payload, final String... flags) {
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);
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
            Set.of(adapter),
            new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    final CommandLine cli = new CommandLine(command);
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    final String[] base = {"--agent", adapter.id(), "--event-stdin", "-C", root.toString()};
    final String[] args = new String[base.length + flags.length];
    System.arraycopy(base, 0, args, 0, base.length);
    System.arraycopy(flags, 0, args, base.length, flags.length);
    return cli.execute(args);
  }
}
