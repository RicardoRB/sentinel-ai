package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.learning.LearningService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import dev.sentinel.infrastructure.cli.VersionProvider;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "check",
    description = "Run all enabled quality gates from sentinel.toml.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    footer = {"", "Exit codes: 0 = all gates passed, 1 = at least one gate failed, 2 = error"})
public class CheckCommand implements Callable<Integer> {

  public enum Format {
    text,
    json
  }

  @Spec private CommandSpec spec;
  @Mixin private ProjectOptions options = new ProjectOptions();

  @Option(
      names = "--format",
      paramLabel = "<format>",
      defaultValue = "text",
      description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
  private Format format;

  @Option(
      names = "--profile",
      split = ",",
      description = "Gate profile(s), comma-separated or repeated (default: default).")
  private List<String> profiles = new ArrayList<>();

  @Option(names = "--fail-fast", description = "Stop after the first gate that does not pass.")
  private boolean failFast;

  @Option(
      names = "--learn-after",
      paramLabel = "<n>",
      description = "Learn recurring failures after n occurrences.")
  private Integer learnAfter;

  private final CheckService service;
  private final TextReportRenderer textRenderer;
  private final JsonReportRenderer jsonRenderer;
  private final LearningService learningService;

  @Inject
  public CheckCommand(
      CheckService service,
      TextReportRenderer textRenderer,
      JsonReportRenderer jsonRenderer,
      LearningService learningService) {
    this.service = service;
    this.textRenderer = textRenderer;
    this.jsonRenderer = jsonRenderer;
    this.learningService = learningService;
  }

  @Override
  public Integer call() {
    final CheckReport report;
    try {
      if (learnAfter != null && learnAfter < 1) {
        throw new picocli.CommandLine.ParameterException(
            spec.commandLine(), "--learn-after must be at least 1");
      }
      profiles = profiles.stream().map(String::trim).toList();
      if (format == Format.text) {
        textRenderer.begin(output());
        report = service.check(options.directory(), profiles, textRenderer, failFast);
        // Render the summary after learning so prompts can be placed before diagnostics.
      } else {
        report = service.check(options.directory(), profiles, failFast);
      }
    } catch (SentinelException e) {
      if (format == Format.json) {
        // stdout stays machine-readable; the human message goes to stderr as well.
        output().println(jsonRenderer.renderError(e.getMessage()));
      }
      throw e;
    }
    final LearningOutcome learning =
        learnAfter == null ? null : learningService.learn(report, learnAfter);
    if (learning != null) {
      learning.warnings().forEach(warning -> error().println(warning));
    }
    if (format == Format.json) {
      output()
          .println(
              jsonRenderer.render(
                  report, profiles.contains("strict"), failFast, learning, learnAfter));
    } else {
      textRenderer.summary(report, learning);
    }
    return report.passed() ? ExitCodes.OK : ExitCodes.FAILED;
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
