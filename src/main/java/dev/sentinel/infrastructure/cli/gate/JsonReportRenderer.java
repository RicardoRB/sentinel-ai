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

public class JsonReportRenderer {
  static final int OUTPUT_TAIL_LINES = 200;
  private final JsonCodec codec;

  @Inject
  public JsonReportRenderer(final JsonCodec codec) {
    this.codec = codec;
  }

  public String render(final CheckReport report) {
    return render(report, false, false, null, null, SeverityThreshold.WARNING);
  }

  public String render(final CheckReport report, final boolean strict) {
    return render(report, strict, false, null, null, SeverityThreshold.WARNING);
  }

  public String render(final CheckReport report, final boolean strict, final boolean failFast) {
    return render(report, strict, failFast, null, null, SeverityThreshold.WARNING);
  }

  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning) {
    return render(report, strict, failFast, learning, null, SeverityThreshold.WARNING);
  }

  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning,
      final Integer threshold) {
    return render(report, strict, failFast, learning, threshold, SeverityThreshold.WARNING);
  }

  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning,
      final SeverityThreshold severity) {
    return render(report, strict, failFast, learning, null, severity);
  }

  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning,
      final Integer threshold,
      final SeverityThreshold severity) {
    final List<CheckDto> checks = new ArrayList<>();
    for (final GateResult result : report.results()) {
      checks.add(CheckDto.from(result, severity));
    }
    final List<PolicyDto> policies = new ArrayList<>();
    new PolicyEvaluator()
        .evaluate(report, strict)
        .forEach(
            policy ->
                policies.add(new PolicyDto(policy.name(), policy.passed(), policy.message())));
    final LearningDto learningDto =
        learning == null ? null : LearningDto.from(learning, threshold == null ? 0 : threshold);
    final ReportDto root =
        new ReportDto(
            2,
            report.status().name(),
            failFast,
            severity.id(),
            ProjectDto.from(report.project()),
            checks,
            policies,
            learningDto,
            report.partialPaths().isEmpty() ? null : new PartialDto(report.partialPaths()));
    return codec.toPrettyJson(root);
  }

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
    public ReportDto {
      checks = List.copyOf(checks);
      policies = List.copyOf(policies);
    }
  }

  @JsonType
  public record PartialDto(List<String> paths) {
    public PartialDto {
      paths = List.copyOf(paths);
    }
  }

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

  @JsonType
  public record PolicyDto(String name, boolean passed, String message) {}

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

  @JsonType
  public static final class ErrorDto {
    public String status;
    public String error;
  }
}
