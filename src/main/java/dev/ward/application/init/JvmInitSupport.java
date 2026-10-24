package dev.ward.application.init;

import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.Map;

/** Init choices for Maven and Gradle projects. */
final class JvmInitSupport implements ToolchainInitSupport {
  private static final JavaGates JAVA = new JavaGates();
  // shortcut: Kotlin gets Java's tools until Ward ships ktlint/detekt; give it its own gates then.
  private static final Map<Language, SourceLanguageGates<BuildToolGates>> LANGUAGES =
      Map.of(Language.JAVA, JAVA, Language.KOTLIN, JAVA);

  @Override
  public Gates gates(final Project project, final EnvironmentInspection environment) {
    final BuildToolGates buildTool = buildToolGates(project, environment);
    return new Gates(
        SourceLanguages.of(LANGUAGES, project).analysisGates(buildTool),
        buildTool.policyGates().stream()
            .filter(gate -> !"api-compat".equals(gate.id()) || project.hasApiBaseline())
            .toList());
  }

  @Override
  public List<String> presetGates(final Project project, final QualityPreset preset) {
    return SourceLanguages.of(LANGUAGES, project).presetGates(preset);
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
