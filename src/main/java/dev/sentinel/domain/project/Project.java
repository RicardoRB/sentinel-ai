package dev.sentinel.domain.project;

import java.nio.file.Path;

/**
 * A detected project.
 *
 * @param root the project root directory
 * @param language its primary language
 * @param buildTool its build tool
 * @param framework its framework
 * @param mavenWrapperAvailable whether the build tool's wrapper, such as {@code mvnw}, is present
 */
public record Project(
    Path root,
    Language language,
    BuildTool buildTool,
    Framework framework,
    boolean mavenWrapperAvailable) {
  /** Creates a project without a Maven Wrapper. */
  public Project(
      final Path root,
      final Language language,
      final BuildTool buildTool,
      final Framework framework) {
    this(root, language, buildTool, framework, false);
  }

  /** Returns whether the wrapper belonging to this project's build tool is available. */
  public boolean wrapperAvailable() {
    return mavenWrapperAvailable;
  }

  /** Returns the wrapper-first executable name for this project's build tool. */
  public String buildExecutable() {
    return switch (buildTool) {
      case MAVEN -> mavenWrapperAvailable ? "./mvnw" : "mvn";
      case GRADLE -> mavenWrapperAvailable ? "./gradlew" : "gradle";
      default -> buildTool.displayName();
    };
  }
}
