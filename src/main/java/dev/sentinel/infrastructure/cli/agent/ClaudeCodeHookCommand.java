package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.learning.LearningService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "claude-code", description = "Run the Claude Code edit/write hook.")
public final class ClaudeCodeHookCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @SuppressWarnings("PMD.ImmutableField")
  @Mixin
  private ProjectOptions options = new ProjectOptions();

  @Option(names = "--learn-after", defaultValue = "3")
  private int threshold;

  private final CheckService checkService;
  private final LearningService learningService;
  private final JsonCodec codec;

  @Inject
  public ClaudeCodeHookCommand(
      final CheckService checkService,
      final LearningService learningService,
      final JsonCodec codec) {
    this.checkService = checkService;
    this.learningService = learningService;
    this.codec = codec;
  }

  @Override
  public Integer call() {
    if (threshold < 1) {
      error().println("--learn-after must be at least 1");
      return ExitCodes.ERROR;
    }
    try {
      final CheckReport report = checkService.check(options.directory());
      final LearningOutcome learning = learningService.learn(report, threshold);
      if (!report.passed()) {
        error().println(failureSummary(report));
        learning.prompts().forEach(prompt -> error().println(prompt.instruction()));
        learning.warnings().forEach(warning -> error().println(warning));
        return ExitCodes.ERROR;
      }
      if (!learning.prompts().isEmpty()) {
        final HookOutput output = new HookOutput();
        output.hookSpecificOutput = new HookSpecificOutput();
        output.hookSpecificOutput.hookEventName = "PostToolUse";
        output.hookSpecificOutput.additionalContext =
            learning.prompts().stream()
                .map(LearningPrompt::instruction)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        output().println(codec.toJson(output));
      }
      learning.warnings().forEach(warning -> error().println(warning));
      return ExitCodes.OK;
    } catch (SentinelException exception) {
      error().println("sentinel: " + exception.getMessage());
      return ExitCodes.ERROR;
    } catch (RuntimeException exception) {
      error().println("sentinel: unexpected error: " + exception.getMessage());
      return ExitCodes.ERROR;
    }
  }

  private static String failureSummary(final CheckReport report) {
    return report.results().stream()
        .filter(result -> !result.passed() && !"SKIPPED".equals(result.status().name()))
        .map(ClaudeCodeHookCommand::summary)
        .reduce((a, b) -> a + System.lineSeparator() + b)
        .orElse("Sentinel quality check failed");
  }

  private static String summary(final GateResult result) {
    return result.name() + ": " + (result.summary() == null ? result.status() : result.summary());
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
