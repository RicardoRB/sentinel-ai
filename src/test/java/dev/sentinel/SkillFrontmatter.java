package dev.sentinel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the YAML frontmatter of a {@code SKILL.md}: top-level scalar keys, and one level of nesting
 * as {@code parent.child}. This is enough for the standard skill fields and is not a YAML parser.
 */
final class SkillFrontmatter {
  private static final String DELIMITER = "---";
  private static final int QUOTE_PAIR = 2;

  private SkillFrontmatter() {}

  static Optional<Map<String, String>> parse(final String content) {
    final List<String> lines = content.lines().toList();
    if (lines.isEmpty() || !DELIMITER.equals(lines.getFirst().strip())) {
      return Optional.empty();
    }
    final List<String> body = lines.subList(1, lines.size());
    final int end = body.stream().map(String::strip).toList().indexOf(DELIMITER);
    return end < 0 ? Optional.empty() : Optional.of(values(body.subList(0, end)));
  }

  private static Map<String, String> values(final List<String> lines) {
    final Map<String, String> values = new HashMap<>();
    String parent = "";
    for (final String line : lines) {
      final int colon = line.indexOf(':');
      if (colon <= 0) {
        continue;
      }
      final String key = line.substring(0, colon).strip();
      final String value = unquote(line.substring(colon + 1).strip());
      if (Character.isWhitespace(line.charAt(0))) {
        putNested(values, parent, key, value);
      } else {
        parent = value.isEmpty() ? key : "";
        values.put(key, value);
      }
    }
    return values;
  }

  private static void putNested(
      final Map<String, String> values, final String parent, final String key, final String value) {
    if (!parent.isEmpty()) {
      values.put(parent + "." + key, value);
    }
  }

  private static String unquote(final String value) {
    if (value.length() < QUOTE_PAIR) {
      return value;
    }
    final char first = value.charAt(0);
    final boolean quoted =
        (first == '"' || first == '\'') && value.charAt(value.length() - 1) == first;
    return quoted ? value.substring(1, value.length() - 1) : value;
  }
}
