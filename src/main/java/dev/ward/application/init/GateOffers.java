package dev.ward.application.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.project.Project;

/** Explains why init does not offer a supported gate for a project. */
final class GateOffers {
  private GateOffers() {}

  /**
   * Checks that init offers gate {@code id} for {@code project}.
   *
   * @throws WardException if {@code id} is an OpenSpec tool, or the project lacks what the gate
   *     needs
   */
  static void require(
      final EnvironmentInspection environment, final Project project, final String id) {
    if ("openspec".equals(id) || "openspec-archived".equals(id)) {
      throw new WardException(
          "OpenSpec validations are tools, not quality gates. Use --tool openspec and --openspec-validation "
              + id
              + ".");
    }
    if ("api-compat".equals(id) && !project.hasApiBaseline()) {
      throw new WardException(
          "api-compat compares against a released baseline, so it is offered only for Maven projects"
              + " whose pom.xml declares <distributionManagement>.");
    }
    requireOpenApiSpec(environment, project, id);
  }

  private static void requireOpenApiSpec(
      final EnvironmentInspection environment, final Project project, final String id) {
    if (SupportedQualityGates.openApiIds().contains(id)
        && environment.openApiSpec(project.root()).isEmpty()) {
      throw new WardException(
          id
              + " checks a committed OpenAPI spec, so it is offered only when Git tracks"
              + " openapi.yml, openapi.yaml, or openapi.json in src/main/resources or the project"
              + " root.");
    }
  }
}
