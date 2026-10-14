package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.learning.LearningService;
import dev.sentinel.application.spec.SpecValidationService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.spec.SpecValidationReport;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

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

  private final CheckService checkService;
  private final LearningService learningService;
  private final JsonCodec codec;
  private final SpecValidationService specService;
  private final ClaudeGuardHook guardHook;
  private final InputStream input;
  private final BooleanSupplier interactiveInput;

  @Inject
  public ClaudeCodeHookCommand(
      final CheckService checkService,
      final LearningService learningService,
      final JsonCodec codec,
      final SpecValidationService specService,
      final ClaudeGuardHook guardHook) {
    this(
        checkService,
        learningService,
        codec,
        specService,
        guardHook,
        System.in,
        () -> System.console() != null && System.console().isTerminal());
  }

  public ClaudeCodeHookCommand(
      final CheckService checkService,
      final LearningService learningService,
      final JsonCodec codec,
      final SpecValidationService specService,
      final ClaudeGuardHook guardHook,
      final InputStream input,
      final BooleanSupplier interactiveInput) {
    this.checkService = checkService;
    this.learningService = learningService;
    this.codec = codec;
    this.specService = specService;
    this.guardHook = guardHook;
    this.input = input;
    this.interactiveInput = interactiveInput;
  }

  // The hook must always answer Claude Code with an exit code, even on unexpected failures.
  @Override
  public Integer call() {
    if (guard) {
      return guardHook.run(options.directory(), readsPayload(), input, output(), error());
    }
    if (threshold < MIN_LEARN_AFTER) {
      error().println("--learn-after must be at least 1");
      return ExitCodes.ERROR;
    }
    final ClaudeHookEvent event =
        readsPayload() ? ClaudeHookEvent.read(input, codec) : new ClaudeHookEvent();
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
    return eventStdin && !interactiveInput.getAsBoolean();
  }

  private Integer executePostToolUse(final ClaudeHookEvent event) {
    final Set<String> excluded = excludeSpec ? SupportedQualityGates.specIds() : Set.of();
    final CheckReport report = checkService.checkExcluding(options.directory(), excluded);
    final LearningOutcome learning = learningService.learn(report, threshold);
    final SpecValidationReport specs =
        event
            .editedPath()
            .map(path -> specService.validatePaths(options.directory(), List.of(), List.of(path)))
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
   * Delivers turn-end spec findings to the agent once per turn. When Claude Code is already
   * continuing because of a stop hook, the findings go to the user instead of blocking again.
   */
  private Integer executeStop(final ClaudeHookEvent event) {
    final SpecValidationReport specs =
        specService.validateConfigured(options.directory(), List.of());
    if (!specs.hasFindings()) {
      return ExitCodes.OK;
    }
    final String findings = ClaudeHookFeedback.specFindings(specs);
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

  @JsonType
  public static final class HookSpecificOutput {
    public String hookEventName;
    public String additionalContext;
  }
}
