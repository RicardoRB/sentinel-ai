package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillParsingTest {
  @Test
  void parsesQuotedAndNestedFrontmatter() {
    assertThat(
            SkillFrontmatter.parse(
                "---\nname: 'demo'\nmetadata:\n  min-ward-version: \"1.2.3\"\n---\nbody\n"))
        .hasValueSatisfying(
            values ->
                assertThat(values)
                    .containsEntry("name", "demo")
                    .containsEntry("metadata.min-ward-version", "1.2.3"));
  }

  @Test
  void ignoresNestedKeysWithoutParentAndLinesWithoutKeys() {
    assertThat(SkillFrontmatter.parse("---\n  orphan: x\nname: a\n: none\nplain text\n---\n"))
        .hasValueSatisfying(values -> assertThat(values).containsOnlyKeys("name"));
  }

  @Test
  void rejectsMissingOrUnterminatedFrontmatter() {
    assertThat(SkillFrontmatter.parse("---\nname: unterminated\n")).isEmpty();
    assertThat(SkillFrontmatter.parse("# Title\n")).isEmpty();
    assertThat(SkillFrontmatter.parse("")).isEmpty();
  }

  @Test
  void readsPluginVersionWhenPresent(@TempDir final Path root) throws IOException {
    assertThat(SkillVersions.plugin(root)).isEmpty();
    Files.createDirectories(root.resolve(".claude-plugin"));
    Files.writeString(root.resolve(".claude-plugin/plugin.json"), "{\"name\": \"x\"}");
    assertThat(SkillVersions.plugin(root)).hasValue("");
  }
}
