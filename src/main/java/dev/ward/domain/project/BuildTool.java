package dev.ward.domain.project;

/** The build tool that defines a project. */
public enum BuildTool {
  MAVEN("Maven"),
  GRADLE("Gradle"),
  NPM("npm"),
  PNPM("pnpm"),
  YARN("Yarn"),
  POETRY("Poetry"),
  PIP("pip"),
  GO("Go"),
  CARGO("Cargo"),
  DOTNET("dotnet");

  private final String displayName;

  BuildTool(final String displayName) {
    this.displayName = displayName;
  }

  /** Returns the name shown to users. */
  public String displayName() {
    return displayName;
  }
}
