package dev.ward.infrastructure.cli.spec;

import dev.ward.application.spec.ApprovedArtifactGuardService;
import dev.ward.domain.spec.GuardDecision;
import dev.ward.domain.spec.GuardRequest;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The {@code ward spec guard} command: decides whether an agent tool call may change approved
 * artifacts.
 */
@Command(
    name = "guard",
    description = {
      "Check whether an agent tool call would modify approved OpenSpec artifacts.",
      "Approved specs (openspec/specs/) and archived changes (openspec/changes/archive/) are",
      "read-only for agents; active changes stay editable. Shell checks are a heuristic, not a",
      "security boundary."
    },
    mixinStandardHelpOptions = true,
    footer = {"", "Exit codes: 0 = allowed, 1 = refused (reason on stdout), 2 = error"})
public final class SpecGuardCommand implements Callable<Integer> {
  private static final long MAX_MODES = 1;

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(
      names = "--cwd",
      paramLabel = "<dir>",
      defaultValue = "",
      description = "Directory relative paths start from (default: the project root).")
  private String workingDirectory;

  @Option(names = "--patch-stdin", description = "Check the patch text read from stdin.")
  private boolean patchStdin;

  @Option(names = "--shell-stdin", description = "Check the shell command read from stdin.")
  private boolean shellStdin;

  @Option(names = "--skill", paramLabel = "<name>", description = "Check a skill invocation.")
  private String skill;

  private List<String> paths = List.of();

  private final ApprovedArtifactGuardService service;
  private final InputStream input;

  /** Creates the command reading the tool call from standard input. */
  @Inject
  public SpecGuardCommand(final ApprovedArtifactGuardService service) {
    this(service, System.in);
  }

  /** Creates the command reading the tool call from {@code input}. */
  public SpecGuardCommand(final ApprovedArtifactGuardService service, final InputStream input) {
    this.service = service;
    this.input = input;
  }

  @Parameters(
      arity = "0..*",
      paramLabel = "<path>",
      description = "Files the agent would write, absolute or relative to --cwd.")
  void paths(final List<String> values) {
    paths = List.copyOf(values);
  }

  @Override
  public Integer call() {
    final long modes =
        Stream.of(patchStdin, shellStdin, skill != null, !paths.isEmpty())
            .filter(Boolean::booleanValue)
            .count();
    if (modes > MAX_MODES) {
      error().println("Use only one of --patch-stdin, --shell-stdin, --skill, or paths.");
      return ExitCodes.ERROR;
    }
    final GuardDecision decision = service.evaluate(request(options.directory()));
    if (decision.allowed()) {
      return ExitCodes.OK;
    }
    output().println(decision.reason());
    return ExitCodes.FAILED;
  }

  private GuardRequest request(final Path start) {
    if (skill != null) {
      return GuardRequest.skill(start, skill);
    }
    if (patchStdin || shellStdin) {
      final String description = patchStdin ? "patch" : "shell command";
      final Optional<String> text = BoundedInput.read(input, BoundedInput.MAX_BYTES);
      if (text.isEmpty()) {
        return GuardRequest.unreadable(start, description);
      }
      return patchStdin
          ? GuardRequest.patch(start, workingDirectory, text.get())
          : GuardRequest.shell(start, workingDirectory, text.get());
    }
    return GuardRequest.paths(start, workingDirectory, paths);
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }

  private PrintWriter error() {
    return spec == null
        ? new PrintWriter(System.err, true, StandardCharsets.UTF_8)
        : spec.commandLine().getErr();
  }
}
