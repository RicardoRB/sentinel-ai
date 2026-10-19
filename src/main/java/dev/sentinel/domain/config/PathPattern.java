package dev.sentinel.domain.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A {@code partial} path pattern relative to the project root: {@code **} matches any number of
 * directories, {@code *} any characters within one name, and {@code ?} one character.
 */
final class PathPattern {
  private static final String ANY_DEPTH = "**";
  private static final char STAR = '*';
  private static final char ANY = '?';
  private static final String SEPARATOR = "/";

  private final List<String> segments;

  private PathPattern(final List<String> segments) {
    this.segments = segments;
  }

  static PathPattern of(final String pattern) {
    return new PathPattern(List.of(pattern.replace('\\', '/').split(SEPARATOR, -1)));
  }

  /** Returns whether {@code path}, root-relative with {@code /} separators, matches. */
  boolean matches(final String path) {
    return matches(0, List.of(path.split(SEPARATOR, -1)), 0);
  }

  private boolean matches(final int segment, final List<String> names, final int name) {
    if (segment == segments.size()) {
      return name == names.size();
    }
    final String current = segments.get(segment);
    if (ANY_DEPTH.equals(current)) {
      return matches(segment + 1, names, name)
          || name < names.size() && matches(segment, names, name + 1);
    }
    return name < names.size()
        && Pattern.matches(regex(current), names.get(name))
        && matches(segment + 1, names, name + 1);
  }

  private static String regex(final String segment) {
    final StringBuilder regex = new StringBuilder(segment.length() * 2);
    for (int index = 0; index < segment.length(); index++) {
      final char character = segment.charAt(index);
      regex.append(
          switch (character) {
            case STAR -> "[^/]*";
            case ANY -> "[^/]";
            default -> Pattern.quote(String.valueOf(character));
          });
    }
    return regex.toString();
  }
}
