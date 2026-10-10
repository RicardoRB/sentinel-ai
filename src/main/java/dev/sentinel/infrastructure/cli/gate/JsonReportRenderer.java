package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
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
  private final JsonCodec codec;

  @Inject
  public JsonReportRenderer(final JsonCodec codec) {
    this.codec = codec;
  }

  public String render(final CheckReport report) {
    return render(report, false, false, null);
  }

  public String render(final CheckReport report, final boolean strict) {
    return render(report, strict, false, null);
  }

  public String render(final CheckReport report, final boolean strict, final boolean failFast) {
    return render(report, strict, failFast, null);
  }

  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning) {
    return render(report, strict, failFast, learning, null);
  }

  public String render(
      final CheckReport report,
      final boolean strict,
      final boolean failFast,
      final LearningOutcome learning,
      final Integer threshold) {
    final List<CheckDto> checks = new ArrayList<>();
    for (final GateResult result : report.results()) {
      checks.add(CheckDto.from(result));
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
            1,
            report.status().name(),
            failFast,
            ProjectDto.from(report.project()),
            checks,
            policies,
            learningDto);
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
      ProjectDto project,
      List<CheckDto> checks,
      List<PolicyDto> policies,
      LearningDto learning) {
    public ReportDto {
      checks = List.copyOf(checks);
      policies = List.copyOf(policies);
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
  public static final class CheckDto {
    public String name;
    public String status;
    public String command;
    public int exitCode;
    public long durationMs;
    public String stdout;
    public String stderr;
    public String summary;
    public String output;
    public Integer errors;
    public Integer warnings;

    static CheckDto from(final GateResult result) {
      final CheckDto dto = new CheckDto();
      dto.name = result.name();
      dto.status = result.status().name();
      dto.command = String.join(" ", result.command());
      dto.exitCode = result.exitCode();
      dto.durationMs = result.duration().toMillis();
      dto.stdout = result.stdout();
      dto.stderr = result.stderr();
      dto.summary = result.summary() == null ? result.status().name() : result.summary();
      dto.output = result.stdout().isBlank() ? result.stderr() : result.stdout();
      dto.errors = result.errors();
      dto.warnings = result.warnings();
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
