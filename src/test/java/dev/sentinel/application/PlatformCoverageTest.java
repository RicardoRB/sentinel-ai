package dev.sentinel.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.application.gate.LanguageGateRegistry;
import dev.sentinel.application.project.ProjectDiscovery;
import dev.sentinel.domain.project.Language;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformCoverageTest {
  @Test
  void discoversMixedRepositoryRoots(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("package.json"), "{}");
    Path python = Files.createDirectories(root.resolve("python"));
    Files.writeString(python.resolve("pyproject.toml"), "[tool.poetry]");
    Path rust = Files.createDirectories(root.resolve("rust"));
    Files.writeString(rust.resolve("Cargo.toml"), "[package]");
    assertThat(
            new ProjectDiscovery(new FileSystemProjectInspection())
                .discover(root).stream().map(project -> project.language()))
        .containsExactly(Language.JAVASCRIPT, Language.PYTHON, Language.RUST);
  }

  @Test
  void projectDiscoveryCarriesMavenWrapperAvailability(@TempDir Path root) throws Exception {
    Files.writeString(root.resolve("pom.xml"), "<project/>");
    Files.writeString(root.resolve("mvnw"), "#!/bin/sh\nexit 0\n");

    var project = new ProjectDiscovery(new FileSystemProjectInspection()).discover(root).getFirst();

    assertThat(project.mavenWrapperAvailable()).isTrue();
  }

  @Test
  void exposesLanguageGateDefaults() {
    var registry = new LanguageGateRegistry();
    assertThat(registry.defaults(Language.JAVA)).contains("tests", "archunit");
    assertThat(registry.defaults(Language.GO)).contains("tests");
  }
}
