package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.policy.PolicyEvaluator;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the JSON document from plain maps/lists (rather than serializing domain records) so the
 * output shape is explicit and needs no reflection, which keeps it native-image friendly.
 */
public class JsonReportRenderer {

  @Inject
  public JsonReportRenderer() {}

  private final JsonMapper mapper =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  public String render(CheckReport report) {
    return render(report, false);
  }

  public String render(CheckReport report, boolean strict) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", 1);
    root.put("status", report.status().name());
    root.put("project", project(report.project()));
    List<Object> checks = new ArrayList<>();
    for (GateResult result : report.results()) {
      Map<String, Object> check = new LinkedHashMap<>();
      check.put("name", result.name());
      check.put("status", result.status().name());
      check.put("command", String.join(" ", result.command()));
      check.put("exitCode", result.exitCode());
      check.put("durationMs", result.duration().toMillis());
      check.put("stdout", result.stdout());
      check.put("stderr", result.stderr());
      check.put("summary", result.summary() == null ? result.status().name() : result.summary());
      check.put("output", result.stdout().isBlank() ? result.stderr() : result.stdout());
      checks.add(check);
    }
    root.put("checks", checks);
    List<Object> policies = new ArrayList<>();
    new PolicyEvaluator()
        .evaluate(report, strict)
        .forEach(
            policy -> {
              Map<String, Object> value = new LinkedHashMap<>();
              value.put("name", policy.name());
              value.put("passed", policy.passed());
              value.put("message", policy.message());
              policies.add(value);
            });
    root.put("policies", policies);
    return mapper.writeValueAsString(root);
  }

  /** Emitted instead of a report when Sentinel could not run the checks at all (exit code 2). */
  public String renderError(String message) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("status", "ERROR");
    root.put("error", message);
    return mapper.writeValueAsString(root);
  }

  private static Map<String, Object> project(Project project) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("language", project.language().name());
    map.put("buildTool", project.buildTool().name());
    map.put("framework", project.framework().name());
    map.put("root", project.root().toString());
    return map;
  }
}
