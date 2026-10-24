package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import java.util.List;
import java.util.Locale;

/** Checks the build tool chain of a project; only the JVM toolchain is checked so far. */
final class BuildToolFindings {
  private BuildToolFindings() {}

  static List<Finding> of(final Project detected, final EnvironmentFacts facts) {
    if (detected.toolchain() != Toolchain.JVM) {
      final String toolName = detected.buildTool().displayName();
      return List.of(
          new Finding(
              toolName.toLowerCase(Locale.ROOT),
              Status.WARNING,
              "Ward does not check the " + toolName + " toolchain yet."));
    }
    return List.of(jvmToolFinding(detected, facts));
  }

  private static Finding jvmToolFinding(final Project detected, final EnvironmentFacts facts) {
    final String toolName = detected.buildTool().displayName();
    final String wrapperName =
        detected.buildTool() == BuildTool.GRADLE ? "Gradle Wrapper" : "Maven Wrapper";
    final String executable = detected.buildTool() == BuildTool.GRADLE ? "gradle" : "mvn";
    final String wrapperFlag =
        detected.buildTool() == BuildTool.GRADLE
            ? "gradleWrapperAvailable"
            : "mavenWrapperAvailable";
    final boolean wrapper = Boolean.parseBoolean(facts.values().get(wrapperFlag));
    final String toolMessage =
        wrapper ? wrapperName + " available." : "System " + toolName + " is required.";
    return new Finding(
        toolName.toLowerCase(Locale.ROOT),
        wrapper || facts.hasExecutable(executable) ? Status.OK : Status.ERROR,
        toolMessage);
  }
}
