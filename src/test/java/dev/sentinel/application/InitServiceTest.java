package dev.sentinel.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitServiceTest {

    private final InitService service = new InitService(new ProjectDetector());

    @Test
    void createsDefaultConfiguration(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);

        InitService.InitResult result = service.init(dir);

        assertThat(result.created()).isTrue();
        assertThat(Files.readString(dir.resolve("sentinel.toml"))).isEqualTo("""
                version = 1

                [quality-gates.tests]
                enabled = true
                command = "./mvnw test"
                """);
    }

    @Test
    void neverOverwritesExistingConfiguration(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);
        Files.writeString(dir.resolve("sentinel.toml"), "version = 1 # mine\n");

        InitService.InitResult result = service.init(dir);

        assertThat(result.created()).isFalse();
        assertThat(Files.readString(dir.resolve("sentinel.toml"))).isEqualTo("version = 1 # mine\n");
    }

    @Test
    void requiresASupportedProject(@TempDir Path dir) {
        assertThatThrownBy(() -> service.init(dir)).isInstanceOf(ProjectNotFoundException.class);
    }
}
