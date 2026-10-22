package dev.ward.domain.project;

/** The primary language of a project. */
public enum Language {
  JAVA("Java"),
  KOTLIN("Kotlin"),
  TYPESCRIPT("TypeScript"),
  JAVASCRIPT("JavaScript"),
  PYTHON("Python"),
  GO("Go"),
  RUST("Rust"),
  CSHARP("C#");

  private final String displayName;

  Language(final String displayName) {
    this.displayName = displayName;
  }

  /** Returns the name shown to users. */
  public String displayName() {
    return displayName;
  }
}
