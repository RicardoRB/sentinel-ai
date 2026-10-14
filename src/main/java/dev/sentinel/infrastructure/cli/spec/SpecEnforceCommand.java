package dev.sentinel.infrastructure.cli.spec;

import dev.sentinel.application.provenance.SpecEnforcementService;
import dev.sentinel.domain.provenance.BootstrapDeclaration;
import dev.sentinel.domain.provenance.EnforcementFinding;
import dev.sentinel.domain.provenance.EnforcementReport;
import dev.sentinel.infrastructure.cli.ExitCodes;
import dev.sentinel.infrastructure.cli.ProjectOptions;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Provider;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "enforce",
    description = {
      "Enforce configured OpenSpec checks and archive-backed provenance against a Git base.",
      "Git history is provenance evidence, not proof of human approval."
    },
    mixinStandardHelpOptions = true,
    footer = {"", "Exit codes: 0 = pass, 1 = policy findings, 2 = prerequisite error"})
public final class SpecEnforceCommand implements Callable<Integer> {
  @Spec private CommandSpec spec;
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(names = "--base", required = true, description = "Explicit Git base revision.")
  private String base;

  @Option(
      names = "--profile",
      split = ",",
      description = "Gate profile(s), comma-separated or repeated (default: default).")
  private final List<String> profiles = new ArrayList<>();

  @Option(
      names = "--bootstrap-baseline",
      description = "Explicit declaration authorizing the first live-spec baseline.")
  private String bootstrap;

  private final Provider<SpecEnforcementService> service;

  @Inject
  public SpecEnforceCommand(final Provider<SpecEnforcementService> service) {
    this.service = service;
  }

  @Override
  public Integer call() {
    final EnforcementReport report =
        service
            .get()
            .enforce(
                options.directory(),
                base,
                profiles.stream().map(String::trim).filter(value -> !value.isBlank()).toList(),
                new BootstrapDeclaration(bootstrap),
                bootstrap != null);
    if (!report.findings().isEmpty()) {
      for (final EnforcementFinding finding : report.findings()) {
        output().println(finding.category() + " " + finding.path() + ": " + finding.message());
      }
    }
    return report.exitCode() == 2
        ? ExitCodes.ERROR
        : report.findings().isEmpty() ? ExitCodes.OK : ExitCodes.FAILED;
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
