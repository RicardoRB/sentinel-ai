package dev.sentinel.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.ProjectFixtures;
import dev.sentinel.application.project.ProjectDiscovery;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// This test intentionally aggregates coverage across the application services.
// Splitting it would duplicate setup and obscure the cross-service scenarios.
class CoverageProjectDiscoveryTest {
  @Test
  void discoversEverySupportedProjectMarker(@TempDir final Path root) throws IOException {
    Files.createDirectories(root.resolve("maven"));
    Files.writeString(root.resolve("maven/pom.xml"), ProjectFixtures.PLAIN_POM);
    Files.createDirectories(root.resolve("gradle"));
    Files.writeString(root.resolve("gradle/build.gradle"), "");
    Files.createDirectories(root.resolve("kotlin"));
    Files.writeString(root.resolve("kotlin/build.gradle.kts"), "");
    Files.createDirectories(root.resolve("typescript"));
    Files.writeString(root.resolve("typescript/tsconfig.json"), "{}");
    Files.createDirectories(root.resolve("javascript"));
    Files.writeString(root.resolve("javascript/package.json"), "{}");
    Files.writeString(root.resolve("javascript/yarn.lock"), "");
    Files.createDirectories(root.resolve("python-poetry"));
    Files.writeString(root.resolve("python-poetry/pyproject.toml"), "");
    Files.createDirectories(root.resolve("python-pip"));
    Files.writeString(root.resolve("python-pip/requirements.txt"), "");
    Files.createDirectories(root.resolve("go"));
    Files.writeString(root.resolve("go/go.mod"), "module example");
    Files.createDirectories(root.resolve("rust"));
    Files.writeString(root.resolve("rust/Cargo.toml"), "[package]");
    Files.createDirectories(root.resolve("csharp"));
    Files.writeString(root.resolve("csharp/app.csproj"), "<Project/>");

    assertThat(new ProjectDiscovery(new FileSystemProjectInspection()).discover(root))
        .extracting(Project::language)
        .containsExactlyInAnyOrder(
            Language.JAVA,
            Language.JAVA,
            Language.KOTLIN,
            Language.TYPESCRIPT,
            Language.JAVASCRIPT,
            Language.PYTHON,
            Language.PYTHON,
            Language.GO,
            Language.RUST,
            Language.CSHARP);
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection()).discover(root.resolve("maven")))
        .hasSize(1);
  }

  @Test
  void projectDiscoverySelectsPackageManagerMarkers(@TempDir final Path root) throws IOException {
    Files.writeString(root.resolve("package.json"), "{}");
    Files.writeString(root.resolve("pnpm-lock.yaml"), "");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root)
                .getFirst()
                .buildTool())
        .isEqualTo(BuildTool.PNPM);
    Files.delete(root.resolve("pnpm-lock.yaml"));
    Files.writeString(root.resolve("yarn.lock"), "");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root)
                .getFirst()
                .buildTool())
        .isEqualTo(BuildTool.YARN);
  }

  @Test
  void architectureGeneratorCreatesAndRollsBackAllStyles(@TempDir final Path root)
      throws IOException {
    Files.createDirectories(root.resolve("src/main/java/com/acme"));
    Files.writeString(
        root.resolve("src/main/java/com/acme/App.java"), "package com.acme; class App {}");
    final ArchitectureTestGenerator generator = new ArchitectureTestGenerator();
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    for (final String style : List.of("layered", "hexagonal", "clean")) {
      final ArchitectureTestChange change = generator.apply(project, style);
      assertThat(change.created()).isTrue();
      assertThat(Files.readString(change.file())).contains("package com.acme;", style);
      generator.rollback(change);
    }
    final ArchitectureTestChange existing = generator.apply(project, "layered");
    assertThat(generator.apply(project, "clean").created()).isFalse();
    generator.rollback(existing);
  }

  @Test
  void architectureGeneratorDefaultsPackageAndPreservesExisting(@TempDir final Path root)
      throws IOException {
    final ArchitectureTestGenerator generator = new ArchitectureTestGenerator();
    final ArchitectureTestChange change =
        generator.apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), "layered");
    assertThat(change.file())
        .isEqualTo(root.resolve("src/test/java/com/example/ArchitectureTest.java"));
    assertThat(Files.readString(change.file())).contains("package com.example;");
    generator.rollback(change);
    Files.createDirectories(change.file().getParent());
    Files.writeString(change.file(), "user test");
    assertThat(
            generator
                .apply(new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE), "clean")
                .created())
        .isFalse();
    generator.rollback(null);
  }
}
