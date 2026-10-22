package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
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
    final WardConfiguration config =
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
    final WardConfiguration config =
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
    final WardConfiguration config =
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
  void parsesPartialConfiguration() throws Exception {
    final WardConfiguration config =
        read(
            reader,
            dir,
            """
                version = 1
                [quality-gates.checkstyle]
                command = ["./mvnw", "checkstyle:check"]
                [quality-gates.checkstyle.partial]
                inputs = ["src/**/*.java"]
                config = ["config/checkstyle.xml"]
                args = ["-Dcheckstyle.includes={paths}"]
                item = "**/{name}"
                separator = ","
                """);

    assertThat(config.gates().get("checkstyle").partial().inputs())
        .containsExactly("src/**/*.java");
    assertThat(config.gates().get("checkstyle").partial().args())
        .containsExactly("-Dcheckstyle.includes={paths}");
  }

  @Test
  void rejectsPartialArgumentsWithoutInputs() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 1
                        [quality-gates.tests]
                        command = "echo test"
                        [quality-gates.tests.partial]
                        args = ["{paths}"]
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests.partial.args")
        .hasMessageContaining("inputs");
  }

  @Test
  void rejectsInvalidPartialValues() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 1
                        [quality-gates.tests]
                        command = "echo test"
                        [quality-gates.tests.partial]
                        inputs = "src/**/*.java"
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests.partial.inputs");
  }

  @Test
  void rejectsPartialItemWithoutPlaceholder() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    """
                        version = 1
                        [quality-gates.tests]
                        command = "echo test"
                        [quality-gates.tests.partial]
                        inputs = ["**"]
                        item = "**/Order.java"
                        """))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("tests.partial.item");
  }

  @Test
  void noGatesIsValid() throws Exception {
    assertThat(read(reader, dir, "version = 1").gates()).isEmpty();
  }

  @Test
  void missingFileMentionsInit() {
    assertThatThrownBy(() -> reader.read(dir.resolve("ward.toml")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("ward init");
  }
}
