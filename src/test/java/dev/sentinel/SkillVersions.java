package dev.sentinel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versions the agent skills are checked against: the project, the plugin, and comparisons. */
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

  /** Compares dotted numeric versions, ignoring any qualifier such as {@code -SNAPSHOT}. */
  static int compare(final String left, final String right) {
    final int[] a = numbers(left);
    final int[] b = numbers(right);
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      final int difference = part(a, i) - part(b, i);
      if (difference != 0) {
        return difference;
      }
    }
    return 0;
  }

  private static int part(final int[] numbers, final int index) {
    return index < numbers.length ? numbers[index] : 0;
  }

  private static int[] numbers(final String version) {
    return Arrays.stream(version.split("-", 2)[0].split("\\."))
        .mapToInt(Integer::parseInt)
        .toArray();
  }
}
