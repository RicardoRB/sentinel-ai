package dev.sentinel.application.init;

import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.gate.SupportedQualityGates;
import dev.sentinel.domain.gate.ToolReports;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Gate choices that run a standalone executable, available only when it is on the path. */
record BinaryToolGates(EnvironmentInspection environment, Project project) {

  /** The executable-backed gates, in wizard order. */
  List<InitGateOption> gates() {
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
