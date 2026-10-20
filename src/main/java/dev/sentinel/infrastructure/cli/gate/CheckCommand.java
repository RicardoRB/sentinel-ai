package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.application.gate.CheckProgressListener;
import dev.sentinel.application.gate.CheckReportExport;
import dev.sentinel.application.gate.CheckRunOptions;
import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.learning.LearningService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.SeverityThreshold;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import dev.sentinel.infrastructure.cli.VersionProvider;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The {@code sentinel check} command: runs the enabled gates and reports them as text, JSON, or
 * SARIF.
 */
@Command(
    name = "check",
    description = "Run all enabled quality gates from sentinel.toml.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    footer = {"", "Exit codes: 0 = all gates passed, 1 = at least one gate failed, 2 = error"})
public class CheckCommand implements Callable<Integer> {

  // The constants are the user-facing --format values.
  /** The machine-readable or human report format selected with {@code --format}. */
  public enum Format {
    TEXT,
    JSON,
    SARIF
  }

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(
      names = "--format",
      paramLabel = "<format>",
      defaultValue = "text",
      converter = FormatConverter.class,
      completionCandidates = FormatCandidates.class,
      description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
  private Format format;

  @Option(
      names = "--severity",
      paramLabel = "<severity>",
      defaultValue = "warning",
      converter = SeverityConverter.class,
      completionCandidates = SeverityCandidates.class,
      description =
          "Minimum finding severity: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
  private SeverityThreshold severity;

  @Option(
      names = "--profile",
      split = ",",
      description = "Gate profile(s), comma-separated or repeated (default: default).")
  private List<String> profiles = new ArrayList<>();

  @Option(
      names = "--path",
      paramLabel = "<path>",
      description = "Edited path to check partially; may be repeated.")
  private final List<Path> paths = new ArrayList<>();

  @Option(names = "--fail-fast", description = "Stop after the first gate that does not pass.")
  private boolean failFast;

  @Option(
      names = "--exclude-spec",
      description = "Skip spec validation gates (openspec, openspec-archived).")
  private boolean excludeSpec;

  @Option(
      names = "--learn-after",
      paramLabel = "<n>",
      description = "Learn recurring failures after n occurrences.")
  private Integer learnAfter;

  @Option(
      names = "--output",
      paramLabel = "<path>",
      description =
          "Write the json or sarif report to this file and print the text report on stdout.")
  private Path outputFile;

  private final CheckService service;
  private final TextReportRenderer textRenderer;
  private final JsonReportRenderer jsonRenderer;
  private final SarifReportRenderer sarifRenderer;
  private final LearningService learningService;
  private final CheckReportExport reportExport;

  static final class FormatConverter implements ITypeConverter<Format> {
    @Override
    public Format convert(final String value) {
      return switch (value.toLowerCase(Locale.ROOT)) {
        case "text" -> Format.TEXT;
        case "json" -> Format.JSON;
        case "sarif" -> Format.SARIF;
        default -> throw new IllegalArgumentException("expected text, json or sarif");
      };
    }
  }

  static final class FormatCandidates implements Iterable<String> {
    @Override
    public Iterator<String> iterator() {
      return List.of("text", "json", "sarif").iterator();
    }
  }

  static final class SeverityConverter implements ITypeConverter<SeverityThreshold> {
    @Override
    public SeverityThreshold convert(final String value) {
      return SeverityThreshold.parse(value);
    }
  }

  static final class SeverityCandidates implements Iterable<String> {
    @Override
    public Iterator<String> iterator() {
      return List.of("error", "warning", "info").iterator();
    }
  }

  /**
   * Creates the command. {@code renderers} write each output format, {@code learningService}
   * records repeated failures, and {@code reportExport} writes {@code --output} files.
   */
  @Inject
  public CheckCommand(
      final CheckService service,
      final ReportRenderers renderers,
      final LearningService learningService,
      final CheckReportExport reportExport) {
    this.service = service;
    this.textRenderer = renderers.text();
    this.jsonRenderer = renderers.json();
    this.sarifRenderer = renderers.sarif();
    this.learningService = learningService;
    this.reportExport = reportExport;
  }

  @Override
  public Integer call() {
    validateOptions();
    profiles = profiles.stream().map(String::trim).toList();
    final CheckReport report = runChecks();
    final LearningOutcome learning =
        learnAfter == null ? null : learningService.learn(report, learnAfter);
    if (learning != null) {
      learning.warnings().forEach(warning -> error().println(warning));
    }
    if (showsText()) {
      textRenderer.summary(report, learning, severity);
    }
    if (format != Format.TEXT) {
      publishMachineReport(renderMachineReport(report, learning));
    }
    return report.passed() ? ExitCodes.OK : ExitCodes.FAILED;
  }

  private void validateOptions() {
    if (learnAfter != null && learnAfter < 1) {
      throw new picocli.CommandLine.ParameterException(
          spec.commandLine(), "--learn-after must be at least 1");
    }
    if (outputFile != null && format == Format.TEXT) {
      throw new picocli.CommandLine.ParameterException(
          spec.commandLine(), "--output requires --format json or --format sarif");
    }
  }

  private void publishMachineReport(final String machineReport) {
    if (outputFile == null) {
      output().println(machineReport);
    } else {
      reportExport.export(outputFile, machineReport + System.lineSeparator());
    }
  }

  private boolean showsText() {
    return format == Format.TEXT || outputFile != null;
  }

  private String renderMachineReport(final CheckReport report, final LearningOutcome learning) {
    return format == Format.SARIF
        ? sarifRenderer.render(report, severity)
        : jsonRenderer.render(
            report,
            new JsonReportOptions(profiles.contains("strict"), failFast, severity),
            learning,
            learnAfter);
  }

  private CheckReport runChecks() {
    try {
      if (showsText()) {
        textRenderer.begin(output());
        // Render the summary after learning so prompts can be placed before diagnostics.
        return runServiceCheck(textRenderer);
      }
      return runServiceCheck(CheckProgressListener.NO_OP);
    } catch (SentinelException e) {
      if (format == Format.JSON && outputFile == null) {
        // stdout stays machine-readable; the human message goes to stderr as well.
        // SARIF writes nothing: an error object would not be a valid SARIF log.
        output().println(jsonRenderer.renderError(e.getMessage()));
      }
      throw e;
    }
  }

  private CheckReport runServiceCheck(final CheckProgressListener listener) {
    if (paths.isEmpty()) {
      return service.check(options.directory(), profiles, listener, failFast, excludedGates());
    }
    final Path workingDirectory = options.directory().toAbsolutePath().normalize();
    final List<Path> resolved =
        paths.stream()
            .map(
                path ->
                    path.isAbsolute()
                        ? path.normalize()
                        : workingDirectory.resolve(path).normalize())
            .toList();
    return service.checkPartial(
        workingDirectory,
        resolved,
        new CheckRunOptions(profiles, listener, failFast, excludedGates()));
  }

  private Set<String> excludedGates() {
    return excludeSpec ? SupportedQualityGates.specIds() : Set.of();
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
