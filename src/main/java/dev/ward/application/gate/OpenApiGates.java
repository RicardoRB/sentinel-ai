package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.ProjectFiles;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.project.Project;
import javax.inject.Inject;

/** Builds the {@code openapi-lint}, {@code openapi-diff}, and {@code openapi-compat} gates. */
public final class OpenApiGates {
  private final Ports ports;

  /** Creates the builder from the ports the OpenAPI gates use. */
  @Inject
  public OpenApiGates(
      final CommandExecutor executor,
      final EnvironmentInspection environment,
      final RevisionFiles revisions,
      final ProjectFiles files) {
    this.ports = new Ports(executor, environment, revisions, files);
  }

  /**
   * Creates the OpenAPI gate {@code id}. With a {@code project}, an {@code openapi-compat} base ref
   * that does not resolve is a configuration error.
   *
   * @throws WardException if the command or options are invalid
   */
  public QualityGate create(
      final String id, final GateConfiguration configuration, final Project project) {
    OpenApiCommandValidation.require(id, configuration.command());
    final OpenApiGateOptions options = configuration.openApi();
    if (options == null) {
      throw new WardException(
          "'quality-gates." + id + ".spec' is required for an enabled OpenAPI gate");
    }
    if (SupportedQualityGates.OPENAPI_LINT.equals(id)) {
      return new OpenApiLintGate(
          id, ports.executor(), ports.files(), configuration.command(), options.spec());
    }
    if (SupportedQualityGates.OPENAPI_DIFF.equals(id)) {
      return new OpenApiDiffGate(
          id,
          ports,
          configuration.command(),
          options.spec(),
          OpenApiBaselines.lastCommit(ports.revisions()));
    }
    if (project != null && ports.revisions().resolve(project.root(), options.base()).isEmpty()) {
      throw new WardException(
          "Quality gate '"
              + id
              + "' cannot resolve base ref '"
              + options.base()
              + "'; set 'base' in ward.toml or fetch it.");
    }
    return new OpenApiDiffGate(
        id,
        ports,
        configuration.command(),
        options.spec(),
        OpenApiBaselines.baseBranch(ports.revisions(), options.base()));
  }

  /** The ports shared by the OpenAPI gates. */
  record Ports(
      CommandExecutor executor,
      EnvironmentInspection environment,
      RevisionFiles revisions,
      ProjectFiles files) {}
}
