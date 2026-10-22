package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentSkillsProblemsTest {
  @Test
  void reportsUnknownInitOption(@TempDir final Path root) throws IOException {
    SkillFixtures.write(root, "Run `ward init --preset strict --no-such-option`.");

    assertThat(SkillFixtures.consistency(root).problems())
        .singleElement()
        .asString()
        .contains(SkillFixtures.SKILL, "--no-such-option");
  }

  @Test
  void ignoresOptionsOfOtherCommands(@TempDir final Path root) throws IOException {
    SkillFixtures.write(root, "Run `ward check --format json`.");

    assertThat(SkillFixtures.consistency(root).problems()).isEmpty();
  }

  @Test
  void checksEveryLineOfTheOptionMap(@TempDir final Path root) throws IOException {
    SkillFixtures.write(root, "body");
    final Path references =
        Files.createDirectories(root.resolve("skills/" + SkillFixtures.SKILL + "/references"));
    Files.writeString(references.resolve("option-map.md"), "| Preset | `--presets` |\n");

    assertThat(SkillFixtures.consistency(root).problems())
        .singleElement()
        .asString()
        .contains("--presets");
  }

  @Test
  void reportsNameMismatch(@TempDir final Path root) throws IOException {
    final Path skill = SkillFixtures.write(root, "body");
    Files.writeString(
        skill, Files.readString(skill).replace("name: " + SkillFixtures.SKILL, "name: other"));

    assertThat(SkillFixtures.consistency(root).problems())
        .singleElement()
        .asString()
        .contains("'other'");
  }

  @Test
  void reportsPluginVersionDrift(@TempDir final Path root) throws IOException {
    SkillFixtures.write(root, SkillFixtures.VERSION, "body", "0.0.8");

    assertThat(SkillFixtures.consistency(root).problems())
        .singleElement()
        .asString()
        .contains("plugin.json", SkillFixtures.VERSION);
  }

  @Test
  void reportsMissingPluginManifest(@TempDir final Path root) throws IOException {
    SkillFixtures.write(root, "body");
    Files.delete(root.resolve(".claude-plugin/plugin.json"));

    assertThat(SkillFixtures.consistency(root).problems())
        .containsExactly(".claude-plugin/plugin.json is missing");
  }

  @Test
  void reportsMissingFrontmatterFields(@TempDir final Path root) throws IOException {
    final Path skill = SkillFixtures.write(root, "body");
    Files.writeString(skill, "---\nname: " + SkillFixtures.SKILL + "\n---\n");

    assertThat(SkillFixtures.consistency(root).problems())
        .anySatisfy(problem -> assertThat(problem).contains("description"))
        .anySatisfy(problem -> assertThat(problem).contains("min-ward-version"));
  }

  @Test
  void reportsMissingSkillFileAndFrontmatter(@TempDir final Path root) throws IOException {
    final Path skill = SkillFixtures.write(root, "body");
    Files.createDirectories(root.resolve("skills/empty-skill"));
    Files.writeString(skill, "# No frontmatter\n");

    assertThat(SkillFixtures.consistency(root).problems())
        .containsExactly(
            SkillFixtures.SKILL + ": SKILL.md has no frontmatter",
            "empty-skill: SKILL.md is missing");
  }
}
