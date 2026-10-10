package dev.sentinel.infrastructure.config;

import static dev.sentinel.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Configuration syntax cases share one reader fixture and are kept as one contract suite.
class TomlParsingTest {
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void parsesDefaultConfiguration() throws Exception {
    final SentinelConfiguration config =
        read(
            reader,
            dir,
            """
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
    final SentinelConfiguration config =
        read(
            reader,
            dir,
            """
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
    final SentinelConfiguration config =
        read(
            reader,
            dir,
            """
                version = 1
                [quality-gates.tests]
                command = ["./mvnw", "test", "-Dtest=A, B"]
                """);

    assertThat(config.gates().get("tests").enabled()).isTrue();
    assertThat(config.gates().get("tests").command())
        .containsExactly("./mvnw", "test", "-Dtest=A, B");
  }

  @Test
  void noGatesIsValid() throws Exception {
    assertThat(read(reader, dir, "version = 1").gates()).isEmpty();
  }

  @Test
  void missingFileMentionsInit() {
    assertThatThrownBy(() -> reader.read(dir.resolve("sentinel.toml")))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("sentinel init");
  }
}
