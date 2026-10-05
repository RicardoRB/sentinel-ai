package dev.sentinel.domain.project;

import java.nio.file.Path;

public record Project(
    Path root,
    Language language,
    BuildTool buildTool,
    Framework framework,
    boolean mavenWrapperAvailable) {
  public Project(Path root, Language language, BuildTool buildTool, Framework framework) {
    this(root, language, buildTool, framework, false);
  }
}
