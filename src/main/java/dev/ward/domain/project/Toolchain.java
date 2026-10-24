package dev.ward.domain.project;

/** The family of build tools that share runtime, setup and build-file handling. */
public enum Toolchain {
  JVM,
  NODE,
  PYTHON,
  GO,
  RUST,
  DOTNET
}
