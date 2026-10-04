package dev.sentinel.application;

import dev.sentinel.domain.project.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCoverageTest {
    @Test void discoversMixedRepositoryRoots(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("package.json"), "{}");
        Path python = Files.createDirectories(root.resolve("python"));
        Files.writeString(python.resolve("pyproject.toml"), "[tool.poetry]");
        Path rust = Files.createDirectories(root.resolve("rust"));
        Files.writeString(rust.resolve("Cargo.toml"), "[package]");
        assertThat(new ProjectDiscovery().discover(root).stream().map(project -> project.language()))
                .containsExactly(Language.JAVASCRIPT, Language.PYTHON, Language.RUST);
    }

    @Test void exposesLanguageGateDefaults() {
        var registry = new LanguageGateRegistry();
        assertThat(registry.defaults(Language.JAVA)).contains("tests", "architecture");
        assertThat(registry.defaults(Language.GO)).contains("tests");
    }
}
