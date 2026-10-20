package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.GateFindings;
import dev.sentinel.domain.gate.GateOutputVisibility;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.SeverityThreshold;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.learning.LearningOutcome;
import dev.sentinel.domain.learning.LearningPrompt;
import dev.sentinel.domain.policy.PolicyEvaluator;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/** Renders a check report as the versioned JSON document {@code check --format json} prints. */
public class JsonReportRenderer {
  static final int OUTPUT_TAIL_LINES = 200;
  private final JsonCodec codec;

  /** Creates the renderer; {@code codec} serializes the report DTOs. */
  @Inject
  public JsonReportRenderer(final JsonCodec codec) {
    this.codec = codec;
  }

  /** Renders {@code report} with default options and no learning section. */
  public String render(final CheckReport report) {
    return render(
        report, new JsonReportOptions(false, false, SeverityThreshold.WARNING), null, null);
  }

  /** Renders {@code report}, applying the strict policies when {@code strict}. */
  public String render(final CheckReport report, final boolean strict) {
    return render(
        report, new JsonReportOptions(strict, false, SeverityThreshold.WARNING), null, null);
  }

  /** Renders {@code report}, recording whether the run stopped after the first failure. */
  public String render(final CheckReport report, final boolean strict, final boolean failFast) {
    return render(
        report, new JsonReportOptions(strict, failFast, SeverityThreshold.WARNING), null, null);
  }

  /** Renders {@code report} with the learning section {@code learning}, when present. */
  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning) {
    return render(
        report, new JsonReportOptions(strict, failFast, SeverityThreshold.WARNING), learning, null);
  }

  /**
   * Renders {@code report} with the learning section and the repeat count that triggered its
   * prompts.
   */
  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning,
      final Integer threshold) {
    return render(
        report,
        new JsonReportOptions(strict, failFast, SeverityThreshold.WARNING),
        learning,
        threshold);
  }

  /** Renders {@code report}, listing findings at or above {@code severity}. */
  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning,
      final SeverityThreshold severity) {
    return render(report, new JsonReportOptions(strict, failFast, severity), learning, null);
  }

  /**
   * Renders the JSON report. {@code learning} adds the learning section when present; {@code
   * threshold} is the repeat count that triggered its prompts, or {@code null} for zero.
   */
  public String render(
      final CheckReport report,
      final JsonReportOptions options,
      final LearningOutcome learning,
      final Integer threshold) {
    final SeverityThreshold severity = options.severity();
    final List<CheckDto> checks = new ArrayList<>();
    for (final GateResult result : report.results()) {
      checks.add(CheckDto.from(result, severity));
    }
    final List<PolicyDto> policies = new ArrayList<>();
    new PolicyEvaluator()
        .evaluate(report, options.strict())
        .forEach(
            policy ->
                policies.add(new PolicyDto(policy.name(), policy.passed(), policy.message())));
    final LearningDto learningDto =
        learning == null ? null : LearningDto.from(learning, threshold == null ? 0 : threshold);
    final ReportDto root =
        new ReportDto(
            2,
            report.status().name(),
            options.failFast(),
            severity.id(),
            ProjectDto.from(report.project()),
            checks,
            policies,
            learningDto,
            report.partialPaths().isEmpty() ? null : new PartialDto(report.partialPaths()));
    return codec.toPrettyJson(root);
  }

  /** Renders the error document printed when the check could not run. */
  public String renderError(final String message) {
    final ErrorDto error = new ErrorDto();
    error.status = "ERROR";
    error.error = message;
    return codec.toPrettyJson(error);
  }

  /** Top-level JSON report; its public fields are the report schema. */
  @JsonType
  public record ReportDto(
      int schemaVersion,
      String status,
      boolean failFast,
      String severity,
      ProjectDto project,
      List<CheckDto> checks,
      List<PolicyDto> policies,
      LearningDto learning,
      PartialDto partial) {
    /** Copies the checks and policies. */
    public ReportDto {
      checks = List.copyOf(checks);
      policies = List.copyOf(policies);
    }
  }

  /**
   * The edited paths a partial check was narrowed to.
   *
   * @param paths project-relative paths, in the order given
   */
  @JsonType
  public record PartialDto(List<String> paths) {
    /** Copies the paths. */
    public PartialDto {
      paths = List.copyOf(paths);
    }
  }

  /** The detected project; its public fields are part of the report schema. */
  @JsonType
  public static final class ProjectDto {
    public String language;
    public String buildTool;
    public String framework;
    public String root;

    static ProjectDto from(final Project project) {
      final ProjectDto dto = new ProjectDto();
      dto.language = project.language().name();
      dto.buildTool = project.buildTool().name();
      dto.framework = project.framework().name();
      dto.root = project.root().toString();
      return dto;
    }
  }

  /** One gate's result in the report schema. */
  @JsonType
  public record CheckDto(
      String name,
      String status,
      String command,
      int exitCode,
      long durationMs,
      String stdout,
      String stderr,
      String summary,
      String output,
      Integer errors,
      Integer warnings,
      List<FindingDto> findings,
      int findingsTotal,
      int findingsHidden,
      boolean findingsTruncated,
      String scope) {

    /** Copies the findings. */
    public CheckDto {
      findings = List.copyOf(findings);
    }

    static CheckDto from(final GateResult result, final SeverityThreshold threshold) {
      final GateOutputVisibility visibility = GateOutputVisibility.of(result, threshold);
      final boolean full = visibility == GateOutputVisibility.FULL;
      final boolean tail = full || visibility == GateOutputVisibility.TAIL;
      final GateFindings.Visible visible = result.findings().visible(threshold);
      return new CheckDto(
          result.name(),
          result.status().name(),
          String.join(" ", result.command()),
          result.exitCode(),
          result.duration().toMillis(),
          full ? result.stdout() : null,
          full ? result.stderr() : null,
          result.summary() == null ? result.status().name() : result.summary(),
          tail ? tail(result.stdout().isBlank() ? result.stderr() : result.stdout()) : null,
          result.errors(),
          result.warnings(),
          visible.items().stream().map(FindingDto::from).toList(),
          visible.total(),
          visible.hidden(),
          visible.truncated(),
          result.scope() == null ? null : result.scope().label());
    }

    /** Keeps the last {@link #OUTPUT_TAIL_LINES} lines; full text stays in stdout and stderr. */
    static String tail(final String text) {
      final List<String> lines = text.lines().toList();
      if (lines.size() <= OUTPUT_TAIL_LINES) {
        return text;
      }
      return String.join("\n", lines.subList(lines.size() - OUTPUT_TAIL_LINES, lines.size()));
    }
  }

  /** One structured finding; its public fields are part of the report schema. */
  @JsonType
  public static final class FindingDto {
    public String tool;
    public String rule;
    public String severity;
    public String message;
    public String file;
    public Integer line;
    public Integer column;
    public String helpUri;

    static FindingDto from(final Finding finding) {
      final FindingDto dto = new FindingDto();
      dto.tool = finding.tool();
      dto.rule = finding.ruleId();
      dto.severity = finding.severity().label();
      dto.message = finding.message();
      dto.file = finding.file();
      dto.line = finding.line();
      dto.column = finding.column();
      dto.helpUri = finding.helpUri();
      return dto;
    }
  }

  /**
   * The outcome of one report policy.
   *
   * @param name the policy name
   * @param passed whether the policy passed
   * @param message why it failed, or a confirmation
   */
  @JsonType
  public record PolicyDto(String name, boolean passed, String message) {}

  /** The learning section: repeated failures that should become project instructions. */
  @JsonType
  public static final class LearningDto {
    public int threshold;
    public List<PromptDto> prompts;

    static LearningDto from(final LearningOutcome outcome, final int threshold) {
      final LearningDto dto = new LearningDto();
      dto.threshold = threshold;
      dto.prompts = outcome.prompts().stream().map(PromptDto::from).toList();
      return dto;
    }
  }

  /**
   * One learning prompt in the report schema.
   *
   * @param key the gate and status that repeated
   * @param gate the gate ID
   * @param occurrences how many consecutive times it failed
   * @param summary a short failure summary
   * @param instruction what the agent is asked to record
   */
  @JsonType
  public record PromptDto(
      String key, String gate, int occurrences, String summary, String instruction) {
    static PromptDto from(final LearningPrompt prompt) {
      return new PromptDto(
          prompt.key(),
          prompt.gate(),
          prompt.occurrences(),
          prompt.summary(),
          prompt.instruction());
    }
  }

  /** The document printed instead of a report when the check could not run. */
  @JsonType
  public static final class ErrorDto {
    public String status;
    public String error;
  }
}
