package dev.ward.infrastructure.config;

import static dev.ward.infrastructure.config.TomlConfigurationFixtures.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** OpenSpec gates accept a relative root; other gates and other values are rejected. */
class TomlOpenSpecRootTest {
  private static final String OPENSPEC =
      "version = 1\n[quality-gates.openspec]\ncommand = \"openspec validate --all\"\n";

  @TempDir Path dir;

  private final TomlConfigurationReader reader = new TomlConfigurationReader();

  @Test
  void relativeRootIsRead() throws IOException {
    assertThat(read(reader, dir, OPENSPEC + "root = \"../..\"").gates().get("openspec").root())
        .isEqualTo("../..");
    assertThat(read(reader, dir, OPENSPEC).gates().get("openspec").root()).isNull();
  }

  @Test
  void absoluteRootIsRejected() {
    assertThatThrownBy(() -> read(reader, dir, OPENSPEC + "root = \"/srv/specs\""))
        .hasMessageContaining("relative to the project root");
  }

  @Test
  void blankOrNonStringRootIsRejected() {
    assertThatThrownBy(() -> read(reader, dir, OPENSPEC + "root = \" \""))
        .hasMessageContaining("non-blank relative path");
    assertThatThrownBy(() -> read(reader, dir, OPENSPEC + "root = 1"))
        .hasMessageContaining("non-blank relative path");
  }

  @Test
  void rootOnOtherGatesIsRejected() {
    assertThatThrownBy(
            () ->
                read(
                    reader,
                    dir,
                    "version = 1\n[quality-gates.tests]\ncommand = \"./mvnw test\"\nroot = \"..\""))
        .hasMessageContaining("only supported for the openspec gates");
  }
}
