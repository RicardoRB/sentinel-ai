package dev.ward.domain.project;

/** The build tool that defines a project. */
public enum BuildTool {
  MAVEN("Maven", Toolchain.JVM),
  GRADLE("Gradle", Toolchain.JVM),
  NPM("npm", Toolchain.NODE),
  PNPM("pnpm", Toolchain.NODE),
  YARN("Yarn", Toolchain.NODE),
  POETRY("Poetry", Toolchain.PYTHON),
  PIP("pip", Toolchain.PYTHON),
  GO("Go", Toolchain.GO),
  CARGO("Cargo", Toolchain.RUST),
  DOTNET("dotnet", Toolchain.DOTNET);

  private final String displayName;
  private final Toolchain toolchain;

  BuildTool(final String displayName, final Toolchain toolchain) {
    this.displayName = displayName;
    this.toolchain = toolchain;
  }

  /** Returns the name shown to users. */
  public String displayName() {
    return displayName;
  }

  /** Returns the toolchain this build tool belongs to. */
  public Toolchain toolchain() {
    return toolchain;
  }
}
