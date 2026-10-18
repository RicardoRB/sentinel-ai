package dev.sentinel.infrastructure.init;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds plugin declarations to a Gradle build script. Gradle accepts {@code plugins {}} only before
 * other statements, so declarations go into the existing top-level block, or into a new block
 * placed after any leading {@code buildscript {}} or {@code pluginManagement {}} block.
 */
final class GradlePluginsBlock {
  static final String MARKER = "// Sentinel-managed quality gate plugins";
  private static final char OPEN = '{';
  private static final char CLOSE = '}';
  private static final Pattern PLUGINS = Pattern.compile("(?m)^plugins\\s*\\{");
  private static final Pattern LEADING_BLOCK =
      Pattern.compile("(?m)^(?:buildscript|pluginManagement)\\s*\\{");

  private GradlePluginsBlock() {}

  static String add(final String script, final List<String> declarations) {
    if (declarations.isEmpty()) {
      return script;
    }
    final StringBuilder lines = new StringBuilder("\n    ").append(MARKER);
    declarations.forEach(declaration -> lines.append("\n    ").append(declaration));
    final Matcher plugins = PLUGINS.matcher(script);
    if (plugins.find()) {
      return script.substring(0, plugins.end()) + lines + script.substring(plugins.end());
    }
    final int position = afterLeadingBlocks(script);
    final String block = "plugins {" + lines + "\n}\n";
    return position == 0
        ? block + "\n" + script
        : script.substring(0, position) + "\n" + block + script.substring(position);
  }

  /** The offset just past the last leading {@code buildscript} or {@code pluginManagement}. */
  private static int afterLeadingBlocks(final String script) {
    int position = 0;
    final Matcher block = LEADING_BLOCK.matcher(script);
    while (block.find(position)) {
      final int end = closingBrace(script, block.end() - 1);
      if (end < 0) {
        return position;
      }
      position = script.indexOf('\n', end) < 0 ? script.length() : script.indexOf('\n', end) + 1;
    }
    return position;
  }

  private static int closingBrace(final String script, final int open) {
    int depth = 0;
    for (int i = open; i < script.length(); i++) {
      final char c = script.charAt(i);
      if (c == OPEN) {
        depth++;
      } else if (c == CLOSE) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }
}
