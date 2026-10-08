package dev.sentinel.application.gate;

import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Generic process-backed gate used by built-in and repository-defined gate registrations. */
public final class CommandQualityGate implements QualityGate {
  private static final Map<String, String> INSTALL_HINTS =
      Map.of(
          "semgrep", "https://semgrep.dev/docs/getting-started/",
          "gitleaks", "https://github.com/gitleaks/gitleaks#installing",
          "trivy", "https://trivy.dev/latest/getting-started/installation/",
          "zap-baseline.py", "https://www.zaproxy.org/docs/docker/baseline-scan/");

  private final String name;
  private final CommandExecutor executor;
  private final List<String> command;

  public CommandQualityGate(
      final String name, final CommandExecutor executor, final List<String> command) {
    this.name = name;
    this.executor = executor;
    this.command = List.copyOf(command);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final GateResult result =
        GateResult.from(name, command, executor.execute(command, project.root()));
    final String hint = command.isEmpty() ? null : INSTALL_HINTS.get(command.getFirst());
    if (hint == null || result.status() != GateStatus.UNAVAILABLE) {
      return result;
    }
    return new GateResult(
        result.name(),
        result.status(),
        result.command(),
        result.exitCode(),
        result.duration(),
        result.stdout(),
        result.stderr(),
        result.summary() + " Install " + command.getFirst() + ": " + hint);
  }
}
