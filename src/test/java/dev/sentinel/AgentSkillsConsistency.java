package dev.sentinel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Checks the agent skills under {@code skills/} against the CLI they drive: frontmatter shape,
 * {@code sentinel init} options, and the plugin version.
 */
final class AgentSkillsConsistency {
  private static final Pattern OPTION = Pattern.compile("(?<![\\w-])--[a-z][a-z0-9-]*");
  private static final String INIT_LINE = "sentinel init";
  private static final String OPTION_MAP = "option-map.md";
  private static final String MIN_VERSION = "metadata.min-sentinel-version";

  private final Path root;
  private final Set<String> initOptions;
  private final List<String> problems = new ArrayList<>();

  AgentSkillsConsistency(final Path root, final Set<String> initOptions) {
    this.root = root;
    this.initOptions = Set.copyOf(initOptions);
  }

  /** Returns every inconsistency found, each naming the file it concerns. */
  List<String> problems() throws IOException {
    problems.clear();
    final Path skills = root.resolve("skills");
    if (!Files.isDirectory(skills)) {
      return List.of("skills/ directory is missing");
    }
    final String projectVersion = SkillVersions.project(root);
    try (Stream<Path> directories = Files.list(skills)) {
      for (final Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
        checkSkill(directory);
      }
    }
    checkPluginVersion(projectVersion);
    return List.copyOf(problems);
  }

  private void checkSkill(final Path directory) throws IOException {
    final String skill = directory.getFileName().toString();
    final Path file = directory.resolve("SKILL.md");
    if (!Files.isRegularFile(file)) {
      problems.add(skill + ": SKILL.md is missing");
      return;
    }
    final Optional<Map<String, String>> frontmatter =
        SkillFrontmatter.parse(Files.readString(file));
    if (frontmatter.isEmpty()) {
      problems.add(skill + ": SKILL.md has no frontmatter");
      return;
    }
    checkFrontmatter(skill, frontmatter.get());
    try (Stream<Path> files = Files.walk(directory)) {
      for (final Path markdown :
          files.filter(path -> path.toString().endsWith(".md")).sorted().toList()) {
        checkOptions(skill, markdown);
      }
    }
  }

  private void checkFrontmatter(final String skill, final Map<String, String> frontmatter) {
    if (!skill.equals(frontmatter.get("name"))) {
      problems.add(skill + ": frontmatter name '" + frontmatter.get("name") + "' must be " + skill);
    }
    if (frontmatter.getOrDefault("description", "").isBlank()) {
      problems.add(skill + ": frontmatter description is empty");
    }
    if (frontmatter.getOrDefault(MIN_VERSION, "").isEmpty()) {
      problems.add(skill + ": " + MIN_VERSION + " is missing");
    }
  }

  /**
   * Options are checked on every line that mentions {@code sentinel init}, and on every line of an
   * {@code option-map.md} reference, which lists only init options.
   */
  private void checkOptions(final String skill, final Path markdown) throws IOException {
    final boolean optionMap = OPTION_MAP.equals(markdown.getFileName().toString());
    for (final String line : Files.readAllLines(markdown, StandardCharsets.UTF_8)) {
      if (optionMap || line.contains(INIT_LINE)) {
        checkLine(skill, markdown, line);
      }
    }
  }

  private void checkLine(final String skill, final Path markdown, final String line) {
    final Matcher matcher = OPTION.matcher(line);
    while (matcher.find()) {
      if (!initOptions.contains(matcher.group())) {
        problems.add(
            skill
                + ": "
                + root.relativize(markdown)
                + " uses unknown sentinel init option "
                + matcher.group());
      }
    }
  }

  private void checkPluginVersion(final String projectVersion) throws IOException {
    final Optional<String> plugin = SkillVersions.plugin(root);
    if (plugin.isEmpty()) {
      problems.add(".claude-plugin/plugin.json is missing");
    } else if (!projectVersion.equals(plugin.get())) {
      problems.add(
          ".claude-plugin/plugin.json version must equal pom.xml version " + projectVersion);
    }
  }
}
