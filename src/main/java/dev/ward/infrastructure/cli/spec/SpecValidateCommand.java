package dev.ward.infrastructure.cli.spec;

import dev.ward.application.spec.SpecValidationService;
import dev.ward.domain.spec.SpecReportRenderer;
import dev.ward.domain.spec.SpecValidationReport;
import dev.ward.infrastructure.cli.ExitCodes;
import dev.ward.infrastructure.cli.ProjectOptions;
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
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** The {@code ward spec validate} command: validates specification artifacts. */
@Command(
    name = "validate",
    description = {
      "Validate specification artifacts.",
      "With paths, validate only the active items those files belong to.",
      "Without paths, run the spec validations enabled in ward.toml."
    },
    mixinStandardHelpOptions = true,
    footer = {"", "Exit codes: 0 = nothing to report, 1 = findings, 2 = error"})
public final class SpecValidateCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Mixin private final ProjectOptions options = new ProjectOptions();

  @Option(
      names = "--profile",
      split = ",",
      description = "Gate profile(s), comma-separated or repeated (default: default).")
  private List<String> profiles = new ArrayList<>();

  private List<String> paths = List.of();

  private final SpecValidationService service;

  /** Creates the command; {@code service} runs the validation. */
  @Inject
  public SpecValidateCommand(final SpecValidationService service) {
    this.service = service;
  }

  @Parameters(
      arity = "0..*",
      paramLabel = "<path>",
      description = "Edited files, absolute or relative to the project root.")
  void paths(final List<String> values) {
    paths = List.copyOf(values);
  }

  @Override
  public Integer call() {
    profiles = profiles.stream().map(String::trim).toList();
    final SpecValidationReport report =
        paths.isEmpty()
            ? service.validateConfigured(options.directory(), profiles)
            : service.validatePaths(options.directory(), profiles, paths);
    if (!report.hasFindings()) {
      return ExitCodes.OK;
    }
    output().println(SpecReportRenderer.render(report));
    return ExitCodes.FAILED;
  }

  private PrintWriter output() {
    return spec == null
        ? new PrintWriter(System.out, true, StandardCharsets.UTF_8)
        : spec.commandLine().getOut();
  }
}
