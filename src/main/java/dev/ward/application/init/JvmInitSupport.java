package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;

/** Init choices for Maven and Gradle projects. */
final class JvmInitSupport implements ToolchainInitSupport {
  @Override
  public Gates gates(final Project project, final EnvironmentInspection environment) {
    final BuildToolGates buildTool = buildToolGates(project, environment);
    return new Gates(
        buildTool.analysisGates(),
        buildTool.policyGates().stream()
            .filter(gate -> !"api-compat".equals(gate.id()) || project.hasApiBaseline())
            .toList());
  }

  @Override
  public String ciSetup(final Project project) {
    return CiWorkflowTemplate.STEP
        + "Set up JDK\n"
        + "        uses: actions/setup-java@v5\n"
        + "        with:\n"
        + "          distribution: temurin\n"
        // shortcut: always JDK 25, which compiles older releases; read the project's toolchain if a
        // build pins a newer one.
        + "          java-version: '25'\n"
        + "          cache: "
        + (project.buildTool() == BuildTool.GRADLE ? "gradle" : "maven")
        + "\n";
  }

  private static BuildToolGates buildToolGates(
      final Project project, final EnvironmentInspection environment) {
    final boolean gradle = project.buildTool() == BuildTool.GRADLE;
    final String executable = project.buildExecutable();
    final String toolName = gradle ? "Gradle" : "Maven";
    final String systemExecutable = gradle ? "gradle" : "mvn";
    final boolean toolAvailable =
        project.wrapperAvailable()
            || environment.inspect(project.root()).hasExecutable(systemExecutable);
    final String availability =
        toolAvailable
            ? toolName + " command available: " + executable
            : toolName
                + " is unavailable; install "
                + toolName
                + " or add a "
                + toolName
                + " Wrapper before running this gate.";
    return new BuildToolGates(gradle, toolAvailable, availability, executable);
  }
}
