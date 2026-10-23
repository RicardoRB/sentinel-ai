package dev.ward.application.gate;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.ProjectFiles;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Reports breaking changes between the working-tree spec and a baseline commit with oasdiff. */
final class OpenApiDiffGate implements QualityGate {
  static final List<String> OASDIFF_CONFIGS =
      List.of(".oasdiff.yaml", ".oasdiff.yml", ".oasdiff.json");
  static final String OASDIFF_CONFIG_VARIABLE = "OASDIFF_CONFIG";
  private static final Logger LOGGER = Logger.getLogger(OpenApiDiffGate.class.getName());

  private final String name;
  private final CommandExecutor executor;
  private final OpenApiGates.Ports ports;
  private final List<String> command;
  private final String spec;
  private final Function<Path, OpenApiBaseline> baseline;

  OpenApiDiffGate(
      final String name,
      final OpenApiGates.Ports ports,
      final List<String> command,
      final String spec,
      final Function<Path, OpenApiBaseline> baseline) {
    this.name = name;
    this.executor = ports.executor();
    this.ports = ports;
    this.command = List.copyOf(command);
    this.spec = spec;
    this.baseline = baseline;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(final Project project) {
    final String refusal = refusal(project.root(), ports.files(), ports.environment());
    if (refusal != null) {
      LOGGER.log(Level.INFO, () -> "event=openapi-diff-refused gate=" + name);
      return OpenApiResults.stopped(name, GateStatus.FAILED, refusal);
    }
    final OpenApiBaseline resolved = baseline.apply(project.root());
    if (!resolved.found()) {
      LOGGER.log(
          Level.INFO,
          () -> "event=openapi-baseline gate=" + name + " status=none reason=" + resolved.reason());
      return OpenApiResults.stopped(name, resolved.status(), resolved.summary());
    }
    final RevisionFiles revisions = ports.revisions();
    if (!revisions.exists(project.root(), resolved.commit(), spec)) {
      LOGGER.log(
          Level.INFO,
          () ->
              "event=openapi-baseline gate="
                  + name
                  + " status=none reason=new-spec revision="
                  + resolved.commit());
      return OpenApiResults.stopped(
          name,
          GateStatus.PASSED,
          "No baseline: " + spec + " does not exist at " + resolved.commit() + ", so it is new.");
    }
    LOGGER.log(
        Level.INFO,
        () ->
            "event=openapi-baseline gate=" + name + " status=found revision=" + resolved.commit());
    return new CommandQualityGate(
            name,
            executor,
            Stream.concat(command.stream(), Stream.of(resolved.commit() + ":./" + spec, spec))
                .toList())
        .execute(project);
  }

  private String refusal(
      final Path root, final ProjectFiles files, final EnvironmentInspection environment) {
    if (!files.exists(root, spec)) {
      return OpenApiResults.missingSpec(spec);
    }
    for (final String config : OASDIFF_CONFIGS) {
      if (files.exists(root, config)) {
        return config
            + " is not allowed: oasdiff loads it on its own, and it can ignore breaking changes."
            + " Remove it.";
      }
    }
    if (environment.inspect(root).isSet(OASDIFF_CONFIG_VARIABLE)) {
      return OASDIFF_CONFIG_VARIABLE
          + " is set: oasdiff would load that configuration, which can ignore breaking changes."
          + " Unset it.";
    }
    return null;
  }
}
