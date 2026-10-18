package dev.sentinel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versions the agent skills are checked against: the project and the plugin. */
final class SkillVersions {
  private static final Pattern PROJECT_VERSION =
      Pattern.compile("<artifactId>sentinel-ai</artifactId>\\s*<version>([^<]+)</version>");
  private static final Pattern PLUGIN_VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

  private SkillVersions() {}

  static String project(final Path root) throws IOException {
    final Matcher matcher = PROJECT_VERSION.matcher(Files.readString(root.resolve("pom.xml")));
    if (!matcher.find()) {
      throw new IllegalStateException("pom.xml has no sentinel-ai project version");
    }
    return matcher.group(1).strip();
  }

  /** The {@code version} of {@code .claude-plugin/plugin.json}, empty when absent. */
  static Optional<String> plugin(final Path root) throws IOException {
    final Path plugin = root.resolve(".claude-plugin/plugin.json");
    if (!Files.isRegularFile(plugin)) {
      return Optional.empty();
    }
    final Matcher matcher = PLUGIN_VERSION.matcher(Files.readString(plugin));
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.of("");
  }
}
