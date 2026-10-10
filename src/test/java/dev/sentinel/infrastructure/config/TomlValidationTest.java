package dev.sentinel.infrastructure.config;

import static dev.sentinel.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Configuration syntax cases share one reader fixture and are kept as one contract suite.
class TomlValidationTest {
  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @TempDir Path dir;

  @Test
  void rejectsInvalidToml() {
    assertThatThrownBy(() -> read(reader, dir, "version = = 1"))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("Invalid TOML");
  }

  @Test
  void rejectsMissingOrUnsupportedVersion() {
    assertThatThrownBy(() -> read(reader, dir, "[quality-gates.tests]\ncommand = \"x\""))
        .hasMessageContaining("version");
    assertThatThrownBy(() -> read(reader, dir, "version = 2")).hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> read(reader, dir, "version = \"1\"")).hasMessageContaining("integer");
  }

  @Test
  void rejectsEnabledGateWithoutCommand() {
    assertThatThrownBy(
            () -> read(reader, dir, "version = 1\n[quality-gates.tests]\nenabled = true"))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("quality-gates.tests.command");
  }

  @Test
  void rejectsBadCommandTypes() {
    assertThatThrownBy(() -> read(reader, dir, "version = 1\n[quality-gates.tests]\ncommand = 5"))
        .hasMessageContaining("string or an array");
    assertThatThrownBy(
            () -> read(reader, dir, "version = 1\n[quality-gates.tests]\ncommand = [\"a\", 1]"))
        .hasMessageContaining("only strings");
    assertThatThrownBy(
            () -> read(reader, dir, "version = 1\n[quality-gates.tests]\ncommand = \"  \""))
        .hasMessageContaining("empty");
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 1\n[quality-gates.tests]\nenabled = \"yes\"\ncommand = \"x\""))
        .hasMessageContaining("true or false");
  }

  @Test
  void parsesGateProfilesAndRejectsInvalidOrLegacyProfiles() throws Exception {
    final SentinelConfiguration defaults =
        read(reader, dir, "version = 1\n[quality-gates.a]\ncommand = 'x'");
    assertThat(defaults.gates().get("a").profiles()).containsExactly("default");
    final SentinelConfiguration configured =
        read(
            reader,
            dir,
            "version = 1\n[quality-gates.a]\nprofiles = [' ci ', 'fast']\nenabled = false");
    assertThat(configured.gates().get("a").profiles()).containsExactly("ci", "fast");
    for (final String value : List.of("[]", "[1]", "['  ']", "'not-an-array'")) {
      assertThatThrownBy(
              () ->
                  read(
                      reader,
                      dir,
                      "version = 1\n[quality-gates.a]\nprofiles = " + value + "\nenabled = false"))
          .isInstanceOf(SentinelException.class)
          .hasMessageContaining("quality-gates.a.profiles");
    }
    assertThatThrownBy(() -> read(reader, dir, "version = 1\n[profiles.old]\ngates = ['a']"))
        .hasMessageContaining("no longer supported")
        .hasMessageContaining("quality-gates");
  }
}
