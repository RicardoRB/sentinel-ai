package dev.sentinel.application.init;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.application.init.InitService;
import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.project.ProjectNotFoundException;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static dev.sentinel.TestProjects.PLAIN_POM;
import static dev.sentinel.TestProjects.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitServiceTest {

    private final InitService service = new InitService(
            new dev.sentinel.application.project.ProjectDetector(new dev.sentinel.infrastructure.project.FileSystemProjectInspection()), Set.of(),
            new InitSetupCatalog(new dev.sentinel.infrastructure.doctor.SystemEnvironmentInspection()), new PomToolConfigurator(), new ArchitectureTestGenerator(),
            new FileConfigurationStorage());

    @Test
    void createsDefaultConfiguration(@TempDir Path dir) throws Exception {
        withPom(dir, PLAIN_POM);

        InitResult result = service.init(dir);

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

        InitResult result = service.init(dir);

        assertThat(result.created()).isFalse();
        assertThat(Files.readString(dir.resolve("sentinel.toml"))).isEqualTo("version = 1 # mine\n");
    }

    @Test
    void requiresASupportedProject(@TempDir Path dir) {
        assertThatThrownBy(() -> service.init(dir)).isInstanceOf(ProjectNotFoundException.class);
    }
}
