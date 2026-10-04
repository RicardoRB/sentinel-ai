package dev.sentinel.infrastructure;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TomlConfigurationReaderTest {

    private final TomlConfigurationReader reader = new TomlConfigurationReader();

    @TempDir
    Path dir;

    private SentinelConfiguration read(String toml) throws Exception {
        Path file = dir.resolve("sentinel.toml");
        Files.writeString(file, toml);
        return reader.read(file);
    }

    @Test
    void parsesDefaultConfiguration() throws Exception {
        SentinelConfiguration config = read("""
                version = 1

                [quality-gates.tests]
                enabled = true
                command = "./mvnw test"
                """);

        assertThat(config.version()).isEqualTo(1);
        assertThat(config.gates()).containsOnlyKeys("tests");
        assertThat(config.gates().get("tests"))
                .isEqualTo(new GateConfiguration(true, List.of("./mvnw", "test")));
    }

    @Test
    void parsesMultipleGatesAndOnlyReturnsEnabledOnes() throws Exception {
        SentinelConfiguration config = read("""
                version = 1

                [quality-gates.tests]
                enabled = true
                command = "./mvnw test"

                [quality-gates.architecture]
                enabled = false
                command = "./mvnw test -Dtest=ArchitectureTest"

                [quality-gates.sonar]
                enabled = false
                command = "sonar analyze agentic"
                """);

        assertThat(config.gates()).containsOnlyKeys("tests", "architecture", "sonar");
        assertThat(config.enabledGates()).containsOnlyKeys("tests");
        assertThat(config.gates().get("architecture").command())
                .containsExactly("./mvnw", "test", "-Dtest=ArchitectureTest");
    }

    @Test
    void acceptsCommandAsArray() throws Exception {
        SentinelConfiguration config = read("""
                version = 1
                [quality-gates.tests]
                command = ["./mvnw", "test", "-Dtest=A, B"]
                """);

        assertThat(config.gates().get("tests").enabled()).isTrue();
        assertThat(config.gates().get("tests").command()).containsExactly("./mvnw", "test", "-Dtest=A, B");
    }

    @Test
    void noGatesIsValid() throws Exception {
        assertThat(read("version = 1").gates()).isEmpty();
    }

    @Test
    void missingFileMentionsInit() {
        assertThatThrownBy(() -> reader.read(dir.resolve("sentinel.toml")))
                .isInstanceOf(SentinelException.class).hasMessageContaining("sentinel init");
    }

    @Test
    void rejectsInvalidToml() {
        assertThatThrownBy(() -> read("version = = 1")).isInstanceOf(SentinelException.class)
                .hasMessageContaining("Invalid TOML");
    }

    @Test
    void rejectsMissingOrUnsupportedVersion() {
        assertThatThrownBy(() -> read("[quality-gates.tests]\ncommand = \"x\"")).hasMessageContaining("version");
        assertThatThrownBy(() -> read("version = 2")).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> read("version = \"1\"")).hasMessageContaining("integer");
    }

    @Test
    void rejectsEnabledGateWithoutCommand() {
        assertThatThrownBy(() -> read("version = 1\n[quality-gates.tests]\nenabled = true"))
                .isInstanceOf(SentinelException.class).hasMessageContaining("quality-gates.tests.command");
    }

    @Test
    void rejectsBadCommandTypes() {
        assertThatThrownBy(() -> read("version = 1\n[quality-gates.tests]\ncommand = 5"))
                .hasMessageContaining("string or an array");
        assertThatThrownBy(() -> read("version = 1\n[quality-gates.tests]\ncommand = [\"a\", 1]"))
                .hasMessageContaining("only strings");
        assertThatThrownBy(() -> read("version = 1\n[quality-gates.tests]\ncommand = \"  \""))
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> read("version = 1\n[quality-gates.tests]\nenabled = \"yes\"\ncommand = \"x\""))
                .hasMessageContaining("true or false");
    }
}
