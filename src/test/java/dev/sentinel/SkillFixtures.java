package dev.sentinel;

import dev.sentinel.infrastructure.cli.SentinelCommand;
import dev.sentinel.infrastructure.cli.init.InitCommand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Temp-directory skill repositories for the agent skills consistency tests. */
final class SkillFixtures {
  static final String VERSION = "0.0.9";
  static final String SKILL = "demo-skill";

  private SkillFixtures() {}

  /** A checker wired to the real {@code sentinel init} and root command options. */
  static AgentSkillsConsistency consistency(final Path root) {
    return new AgentSkillsConsistency(
        root, CliOptionNames.of(InitCommand.class, SentinelCommand.class));
  }

  /** Writes a pom, a plugin manifest, and one skill with the given minimum version and body. */
  static Path write(
      final Path root, final String minimum, final String body, final String pluginVersion)
      throws IOException {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project>\n  <artifactId>sentinel-ai</artifactId>\n  <version>"
            + VERSION
            + "</version>\n</project>\n");
    Files.createDirectories(root.resolve(".claude-plugin"));
    Files.writeString(
        root.resolve(".claude-plugin/plugin.json"),
        "{\n  \"name\": \"sentinel\",\n  \"version\": \"" + pluginVersion + "\"\n}\n");
    final Path skill = Files.createDirectories(root.resolve("skills/" + SKILL));
    return Files.writeString(
        skill.resolve("SKILL.md"),
        String.join(
            "\n",
            "---",
            "name: " + SKILL,
            "description: Use when testing the consistency check.",
            "metadata:",
            "  min-sentinel-version: \"" + minimum + "\"",
            "---",
            body,
            ""));
  }

  /** Writes a consistent fixture whose skill body is {@code body}. */
  static Path write(final Path root, final String body) throws IOException {
    return write(root, VERSION, body, VERSION);
  }
}
