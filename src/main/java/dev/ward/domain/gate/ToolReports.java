package dev.ward.domain.gate;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;

/** Report locations that generated gate commands write and finding extraction reads. */
public final class ToolReports {
  private ToolReports() {}

  /** Returns {@code build} for Gradle projects and {@code target} otherwise. */
  public static String buildDirectory(final Project project) {
    return project.buildTool() == BuildTool.GRADLE ? "build" : "target";
  }

  /** Returns the project-relative path of the gitleaks SARIF report. */
  public static String gitleaksSarif(final Project project) {
    return buildDirectory(project) + "/gitleaks.sarif";
  }
}
