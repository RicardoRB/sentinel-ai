package dev.ward.infrastructure.traceability;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks comments and literals while preserving source offsets and line numbers. */
final class JavaSourceSanitizer {
  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
  private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\r\\n]*");
  private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
  private static final Pattern CHARACTER = Pattern.compile("'(?:\\\\.|[^'\\\\])*'");

  private JavaSourceSanitizer() {}

  static String mask(final String source) {
    String masked = maskMatches(source, BLOCK_COMMENT);
    masked = maskMatches(masked, LINE_COMMENT);
    masked = maskMatches(masked, STRING);
    return maskMatches(masked, CHARACTER);
  }

  private static String maskMatches(final String source, final Pattern pattern) {
    final Matcher matcher = pattern.matcher(source);
    final StringBuilder result = new StringBuilder(source);
    while (matcher.find()) {
      maskRange(result, matcher.start(), matcher.end());
    }
    return result.toString();
  }

  private static void maskRange(final StringBuilder source, final int start, final int end) {
    for (int index = start; index < end; index++) {
      if (source.charAt(index) != '\n' && source.charAt(index) != '\r') {
        source.setCharAt(index, ' ');
      }
    }
  }
}
