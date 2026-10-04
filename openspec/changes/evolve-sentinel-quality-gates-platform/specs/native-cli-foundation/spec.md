## Purpose

Provide a small, framework-independent command-line foundation that detects supported projects, initializes configuration safely, and runs identically from the JVM and native executable.

## ADDED Requirements

### Requirement: Native CLI foundation
Sentinel SHALL provide `--help`, `--version`, `detect`, and `init` commands from a Java 25-compatible native executable.

#### Scenario: Help and version
- **WHEN** a user invokes `sentinel --help` or `sentinel --version`
- **THEN** Sentinel prints concise usage or version information and exits with status 0

### Requirement: Supported project detection
Sentinel SHALL detect Java projects using Maven markers, identify Spring Boot from project metadata without requiring Spring Boot at runtime, report Maven Wrapper availability, and leave the project unchanged.

#### Scenario: Maven project with Spring Boot and wrapper
- **WHEN** `detect` runs in a project containing `pom.xml`, Spring Boot metadata, and `mvnw`
- **THEN** it reports Java, Maven, Spring Boot, wrapper availability, and the project root

#### Scenario: Unsupported project
- **WHEN** `detect` runs where no supported project is found
- **THEN** it reports that no supported project was detected and exits with a non-success status

### Requirement: Safe initialization
Sentinel SHALL create a versioned `sentinel.toml` with the initial tests gate enabled and SHALL refuse to overwrite an existing configuration silently.

#### Scenario: Initialize missing configuration
- **WHEN** `init` runs in a supported project without `sentinel.toml`
- **THEN** it creates the initial configuration and reports success

#### Scenario: Existing configuration
- **WHEN** `init` runs while `sentinel.toml` already exists
- **THEN** it leaves the file unchanged and reports an actionable error

### Requirement: Framework-independent architecture
Sentinel SHALL NOT depend on Spring Framework or Spring Boot, SHALL wire dependencies through an explicit Guice composition root using constructor injection, and SHALL keep domain types independent of Guice, Picocli, process APIs, and filesystem implementations.

#### Scenario: Dependency boundary check
- **WHEN** the architecture tests run
- **THEN** they fail if a domain type references a framework, CLI, process, or filesystem implementation package or if Spring appears in the dependency tree

### Requirement: Typed configuration
Sentinel SHALL parse `sentinel.toml` into immutable typed models, reject unknown keys and unsupported versions with an actionable error, and parse identically on the JVM and native executable.

#### Scenario: Malformed configuration
- **WHEN** `sentinel.toml` is syntactically invalid or has an unsupported `version`
- **THEN** the command reports the problem and exits with a configuration error

### Requirement: Native parity
The supported foundation commands SHALL behave consistently when invoked from the native executable, without requiring a JVM at runtime.

#### Scenario: Native command execution
- **WHEN** the built native executable runs each foundation command
- **THEN** its output, exit status, and filesystem effects satisfy the same command contract as the JVM build
