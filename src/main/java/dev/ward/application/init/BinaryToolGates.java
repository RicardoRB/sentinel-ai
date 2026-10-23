package dev.ward.application.init;

import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.gate.ToolReports;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gate choices that run a standalone executable, available only when it is on the path. */
record BinaryToolGates(EnvironmentInspection environment, Project project) {
  private static final String SPEC = "spec";

  /** The executable-backed gates, in wizard order. */
  List<InitGateOption> gates() {
    final List<InitGateOption> gates = new ArrayList<>(securityGates());
    environment.openApiSpec(project.root()).ifPresent(spec -> gates.addAll(openApiGates(spec)));
    return List.copyOf(gates);
  }

  /** The contract gates, offered only for a Git-tracked OpenAPI spec they can check. */
  private List<InitGateOption> openApiGates(final String spec) {
    final List<String> diff = List.of("oasdiff", "breaking", "--fail-on", "ERR");
    return List.of(
        withSettings(
            option(
                SupportedQualityGates.OPENAPI_LINT,
                "Lints the committed OpenAPI spec with every built-in vacuum rule.",
                List.of("vacuum", "lint", "-z", "-n", "warn"),
                "Install vacuum."),
            Map.of(SPEC, spec)),
        withSettings(
            option(
                SupportedQualityGates.OPENAPI_DIFF,
                "Reports breaking changes in uncommitted OpenAPI spec edits with oasdiff.",
                diff,
                "Install oasdiff."),
            Map.of(SPEC, spec)),
        withSettings(
            option(
                SupportedQualityGates.OPENAPI_COMPAT,
                "Reports breaking OpenAPI changes on the branch against origin/main with oasdiff.",
                diff,
                "Install oasdiff, and fetch origin/main before checking."),
            orderedSettings(spec)));
  }

  private static Map<String, String> orderedSettings(final String spec) {
    final Map<String, String> settings = new LinkedHashMap<>();
    settings.put(SPEC, spec);
    settings.put("base", OpenApiGateOptions.DEFAULT_BASE);
    return settings;
  }

  private static InitGateOption withSettings(
      final InitGateOption option, final Map<String, String> settings) {
    return new InitGateOption(
        option.id(),
        option.description(),
        option.command(),
        option.available(),
        option.availabilityMessage(),
        settings);
  }

  private List<InitGateOption> securityGates() {
    return List.of(
        option(
            "semgrep",
            "Runs SAST and custom security rules with Semgrep.",
            List.of("semgrep", "scan", "--config", "p/java", "--error"),
            "Install Semgrep (needs network access to fetch the p/java ruleset)."),
        option(
            "gitleaks",
            "Detects committed secrets with Gitleaks.",
            List.of(
                "gitleaks",
                "detect",
                "--no-banner",
                "--redact",
                "--report-format",
                "sarif",
                "--report-path",
                ToolReports.gitleaksSarif(project)),
            "Install Gitleaks."),
        option(
            "zap",
            "Runs an OWASP ZAP baseline DAST scan against a running application; replace <TARGET_URL> in -t with an authorized target before running.",
            List.of("zap-baseline.py", "-t", SupportedQualityGates.ZAP_TARGET_PLACEHOLDER),
            "Install ZAP and replace "
                + SupportedQualityGates.ZAP_TARGET_PLACEHOLDER
                + " with a target you are authorized to scan before running."),
        option(
            "trivy",
            "Scans dependencies, containers, and IaC with Trivy.",
            List.of("trivy", "fs", "--exit-code", "1", "."),
            "Install Trivy."));
  }

  private InitGateOption option(
      final String id, final String description, final List<String> command, final String hint) {
    final boolean available = environment.inspect(project.root()).hasExecutable(command.getFirst());
    final String message =
        available
            ? "Executable available: " + command.getFirst()
            : command.getFirst() + " is not on the path. " + hint;
    return new InitGateOption(id, description, command, available, message);
  }
}
