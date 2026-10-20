package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.learning.LearningService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.spec.SpecValidationReport;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** The {@code sentinel hook claude-code} command run by the generated Claude Code hooks. */
@Command(name = "claude-code", description = "Run the Claude Code edit/write and stop hook.")
public final class ClaudeCodeHookCommand implements Callable<Integer> {
  private static final Logger LOGGER = Logger.getLogger(ClaudeCodeHookCommand.class.getName());
  private static final int MIN_LEARN_AFTER = 1;

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(names = "--learn-after", defaultValue = "3")
  private int threshold;

  @Option(
      names = "--exclude-spec",
      description = "Skip spec gates in the quality check; spec validation runs separately.")
  private boolean excludeSpec;

  @Option(
      names = "--guard-approved-specs",
      description =
          "Act as the PreToolUse guard for approved OpenSpec artifacts; runs no quality checks.")
  private boolean guard;

  @Option(
      names = "--event-stdin",
      description = "Read the Claude Code hook event JSON from stdin (used by generated hooks).")
  private boolean eventStdin;

  private final HookChecks hookChecks;
  private final LearningService learningService;
  private final JsonCodec codec;
  private final ClaudeGuardHook guardHook;
  private final HookStdin stdin;

  /**
   * Creates the hook command reading its event from standard input. {@code hookChecks} runs the
   * quality gates and spec validation, {@code learningService} records repeated failures, and
   * {@code guardHook} answers approved-artifact guard requests.
   */
  @Inject
  public ClaudeCodeHookCommand(
      final HookChecks hookChecks,
      final LearningService learningService,
      final JsonCodec codec,
      final ClaudeGuardHook guardHook) {
    this(hookChecks, learningService, codec, guardHook, HookStdin.system());
  }

  ClaudeCodeHookCommand(
      final HookChecks hookChecks,
      final LearningService learningService,
      final JsonCodec codec,
      final ClaudeGuardHook guardHook,
      final HookStdin stdin) {
    this.hookChecks = hookChecks;
    this.learningService = learningService;
    this.codec = codec;
    this.guardHook = guardHook;
    this.stdin = stdin;
  }

  // The hook must always answer Claude Code with an exit code, even on unexpected failures.
  @Override
  public Integer call() {
    if (guard) {
      return guardHook.run(options.directory(), readsPayload(), stdin.stream(), output(), error());
    }
    if (threshold < MIN_LEARN_AFTER) {
      error().println("--learn-after must be at least 1");
      return ExitCodes.ERROR;
    }
    final ClaudeHookEvent event =
        readsPayload() ? ClaudeHookEvent.read(stdin.stream(), codec) : new ClaudeHookEvent();
    final boolean stop = event.isStop();
    LOGGER.log(Level.INFO, () -> "event=claude-hook-start kind=" + (stop ? "stop" : "post-tool"));
    final FutureTask<Integer> execution =
        new FutureTask<>(() -> stop ? executeStop(event) : executePostToolUse(event));
    execution.run();
    try {
      return execution.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return stop ? stopFailure(exception) : reportFailure(exception);
    } catch (ExecutionException exception) {
      return stop ? stopFailure(exception.getCause()) : reportFailure(exception.getCause());
    }
  }

  /** Whether stdin carries a hook payload: only when asked to and not an interactive terminal. */
  private boolean readsPayload() {
    return eventStdin && !stdin.interactive().getAsBoolean();
  }

  private Integer executePostToolUse(final ClaudeHookEvent event) {
    final Set<String> excluded = excludeSpec ? SupportedQualityGates.specIds() : Set.of();
    final CheckReport report =
        hookChecks
            .checks()
            .checkPartial(
                options.directory(),
                event
                    .editedPath()
                    .map(path -> List.of(options.directory().toAbsolutePath().resolve(path)))
                    .orElseGet(List::of),
                excluded);
    final LearningOutcome learning = learningService.learn(report, threshold);
    final SpecValidationReport specs =
        event
            .editedPath()
            .map(
                path ->
                    hookChecks.specs().validatePaths(options.directory(), List.of(), List.of(path)))
            .orElse(SpecValidationReport.EMPTY);
    final boolean failed = !report.passed() || specs.hasFindings();
    if (failed) {
      ClaudeHookFeedback.failures(report, specs, learning).forEach(error()::println);
    } else if (!learning.prompts().isEmpty()) {
      output()
          .println(
              ClaudeHookResponses.postToolUseContext(
                  codec, ClaudeHookFeedback.learningContext(learning)));
    }
    learning.warnings().forEach(warning -> error().println(warning));
    return failed ? ExitCodes.ERROR : ExitCodes.OK;
  }

  /**
   * Runs the full {@code default} check and spec validation at turn end, and delivers their
   * findings to the agent once per turn. When Claude Code is already continuing because of a stop
   * hook, the findings go to the user instead of blocking again.
   */
  private Integer executeStop(final ClaudeHookEvent event) {
    final CheckReport report =
        hookChecks.checks().checkExcluding(options.directory(), SupportedQualityGates.specIds());
    final SpecValidationReport specs =
        hookChecks.specs().validateConfigured(options.directory(), List.of());
    if (report.passed() && !specs.hasFindings()) {
      return ExitCodes.OK;
    }
    final String findings = ClaudeHookFeedback.turnEnd(report, specs);
    if (event.stopHookActive) {
      LOGGER.log(Level.INFO, "event=claude-stop-reported reason=already-continued");
      output().println(ClaudeHookResponses.systemMessage(codec, findings));
    } else {
      LOGGER.log(Level.INFO, "event=claude-stop-blocked");
      output().println(ClaudeHookResponses.blockStop(codec, findings));
    }
    return ExitCodes.OK;
  }

  /** A turn-end infrastructure failure is shown to the user and never blocks the agent. */
  private Integer stopFailure(final Throwable failure) {
    output().println(ClaudeHookResponses.systemMessage(codec, failureMessage(failure)));
    return ExitCodes.OK;
  }

  private Integer reportFailure(final Throwable failure) {
    error().println(failureMessage(failure));
    return ExitCodes.ERROR;
  }

  private static String failureMessage(final Throwable failure) {
    return failure instanceof SentinelException
        ? "sentinel: " + failure.getMessage()
        : "sentinel: unexpected error: " + failure.getMessage();
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

  /** PostToolUse hook output; its JSON shape is a published contract. */
  @JsonType
  public static final class HookOutput {
    public HookSpecificOutput hookSpecificOutput;
  }

  /** The event-specific part of a Claude Code hook response; its public fields are serialized. */
  @JsonType
  public static final class HookSpecificOutput {
    public String hookEventName;
    public String additionalContext;
  }
}
