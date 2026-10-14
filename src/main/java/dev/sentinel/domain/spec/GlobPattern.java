package dev.sentinel.domain.spec;

import java.util.List;
import java.util.regex.Pattern;

/** Matches shell pattern path segments without touching the filesystem. */
public final class GlobPattern {
  private static final String ANY_DEPTH = "**";
  private static final char STAR = '*';
  private static final char ANY = '?';
  private static final char CLASS = '[';
  private static final char CLASS_END = ']';
  private static final char ALTERNATIVES = '{';
  private static final char ALTERNATIVES_END = '}';
  private static final char NEGATE = '!';

  private GlobPattern() {}

  /** The characters that make an unquoted shell word a pattern. */
  public static int firstPatternCharacter(final String text) {
    for (int index = 0; index < text.length(); index++) {
      final char character = text.charAt(index);
      if (character == STAR
          || character == ANY
          || character == CLASS
          || character == ALTERNATIVES) {
        return index;
      }
    }
    return -1;
  }

  /**
   * Returns how many leading {@code names} the {@code pattern} segments can match: all names when
   * the pattern reaches or passes them, fewer when it stops above them, or -1 when it cannot match.
   */
  public static int reach(final List<String> pattern, final List<String> names) {
    int index = 0;
    while (index < pattern.size() && index < names.size()) {
      final String segment = pattern.get(index);
      if (ANY_DEPTH.equals(segment)) {
        return names.size();
      }
      if (!matches(segment, names.get(index))) {
        return -1;
      }
      index++;
    }
    return index;
  }

  /** Returns whether one pattern segment matches one file name. */
  public static boolean matches(final String segment, final String name) {
    return Pattern.matches(regex(segment), name);
  }

  private static String regex(final String segment) {
    final StringBuilder regex = new StringBuilder();
    int index = 0;
    while (index < segment.length()) {
      index = translate(segment, index, regex);
    }
    return regex.toString();
  }

  /** Appends the regex for the pattern element at {@code index} and returns the next index. */
  private static int translate(final String segment, final int index, final StringBuilder regex) {
    final char character = segment.charAt(index);
    final int close =
        switch (character) {
          case CLASS -> segment.indexOf(CLASS_END, index + 2);
          case ALTERNATIVES -> segment.indexOf(ALTERNATIVES_END, index + 1);
          default -> -1;
        };
    if (character == STAR) {
      regex.append("[^/]*");
    } else if (character == ANY) {
      regex.append("[^/]");
    } else if (close > 0 && character == CLASS) {
      final String body = segment.substring(index + 1, close);
      regex
          .append('[')
          .append(body.charAt(0) == NEGATE ? "^" + quoteClass(body.substring(1)) : quoteClass(body))
          .append(']');
      return close + 1;
    } else if (close > 0) {
      final List<String> options = List.of(segment.substring(index + 1, close).split(",", -1));
      regex
          .append("(?:")
          .append(String.join("|", options.stream().map(GlobPattern::regex).toList()))
          .append(')');
      return close + 1;
    } else {
      regex.append(Pattern.quote(String.valueOf(character)));
    }
    return index + 1;
  }

  private static String quoteClass(final String body) {
    return body.replace("\\", "\\\\").replace("[", "\\[").replace("&&", "\\&\\&");
  }
}
