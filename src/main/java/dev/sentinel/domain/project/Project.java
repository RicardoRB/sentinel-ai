package dev.sentinel.domain.project;

import java.nio.file.Path;

public record Project(
    Path root,
    Language language,
    BuildTool buildTool,
    Framework framework,
    boolean mavenWrapperAvailable) {
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
