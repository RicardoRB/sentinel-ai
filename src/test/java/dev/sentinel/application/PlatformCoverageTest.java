package dev.sentinel.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.gate.LanguageGateRegistry;
import dev.sentinel.application.project.ProjectDiscovery;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformCoverageTest {
  @Test
  void discoversMixedRepositoryRoots(@TempDir final Path root) throws Exception {
    Files.writeString(root.resolve("package.json"), "{}");
    final Path python = Files.createDirectories(root.resolve("python"));
    Files.writeString(python.resolve("pyproject.toml"), "[tool.poetry]");
    final Path rust = Files.createDirectories(root.resolve("rust"));
    Files.writeString(rust.resolve("Cargo.toml"), "[package]");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root).stream().map(Project::language))
        .containsExactly(Language.JAVASCRIPT, Language.PYTHON, Language.RUST);
  }

  @Test
  void projectDiscoveryCarriesMavenWrapperAvailability(@TempDir final Path root) throws Exception {
    Files.writeString(root.resolve("pom.xml"), "<project/>");
    Files.writeString(root.resolve("mvnw"), "#!/bin/sh\nexit 0\n");

    final Project project =
        new ProjectDiscovery(new FileSystemProjectInspection()).discover(root).getFirst();

    assertThat(project.mavenWrapperAvailable()).isTrue();
  }

  @Test
  void exposesLanguageGateDefaults() {
    final LanguageGateRegistry registry = new LanguageGateRegistry();
    assertThat(registry.defaults(Language.JAVA)).contains("tests", "archunit");
    assertThat(registry.defaults(Language.GO)).contains("tests");
  }
}
