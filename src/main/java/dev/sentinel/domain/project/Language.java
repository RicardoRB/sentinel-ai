package dev.sentinel.domain.project;

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

  Language(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
