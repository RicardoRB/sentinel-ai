package dev.sentinel.quality;

import java.util.ArrayList;
import java.util.List;

final class SuppressionSourceDetector {
  private static final int ONE = 1;
  private static final int TWO = 2;
  private static final int THREE = 3;
  private static final char DOUBLE_QUOTE = '"';
  private static final char SINGLE_QUOTE = '\'';
  private static final char AT = '@';
  private static final char SLASH = '/';
  private static final char NEWLINE = '\n';
  private static final char BACKSLASH = '\\';

  private SuppressionSourceDetector() {}

  static List<Diagnostic> find(final String source) {
    final List<Diagnostic> diagnostics = new ArrayList<>();
    int line = 1;
    int index = 0;
    while (index < source.length()) {
      final Position next = scanToken(source, index, line, diagnostics);
      index = next.index;
      line = next.line;
    }
    return List.copyOf(diagnostics);
  }

  private static Position scanToken(
      final String source, final int index, final int line, final List<Diagnostic> diagnostics) {
    final char current = source.charAt(index);
    if (current == DOUBLE_QUOTE) {
      if (source.startsWith("\"\"\"", index)) {
        final int end = textBlockEnd(source, index);
        return new Position(end, line + lines(source, index, end));
      }
      final int end = stringEnd(source, index, DOUBLE_QUOTE);
      return new Position(end, line + lines(source, index, end));
    }
    if (current == SINGLE_QUOTE) {
      final int end = stringEnd(source, index, SINGLE_QUOTE);
      return new Position(end, line + lines(source, index, end));
    }
    if (isCommentStart(source, index)) {
      final int end =
          source.charAt(index + ONE) == SLASH
              ? lineCommentEnd(source, index)
              : blockCommentEnd(source, index);
      addCommentDiagnostic(source.substring(index, end), line, diagnostics);
      return new Position(end, line + lines(source, index, end));
    }
    if (current == AT) {
      addAnnotation(source, index, line, diagnostics);
      return new Position(index + 1, line);
    }
    return new Position(index + ONE, line + (current == NEWLINE ? ONE : 0));
  }

  private static boolean isCommentStart(final String source, final int index) {
    return index + ONE < source.length()
        && source.charAt(index) == SLASH
        && (source.charAt(index + ONE) == SLASH || source.charAt(index + ONE) == '*');
  }

  private static void addAnnotation(
      final String source, final int index, final int line, final List<Diagnostic> diagnostics) {
    final String annotation = annotationName(source, index + ONE);
    final int separator = annotation.lastIndexOf('.');
    final String simpleName = annotation.substring(separator + ONE);
    if ("SuppressWarnings".equals(simpleName) || "SuppressFBWarnings".equals(simpleName)) {
      diagnostics.add(new Diagnostic(line, annotation));
    }
  }

  private static void addCommentDiagnostic(
      final String comment, final int line, final List<Diagnostic> diagnostics) {
    final int marker = comment.indexOf("NOPMD");
    if (marker >= 0) {
      diagnostics.add(new Diagnostic(line + lines(comment, 0, marker), "NOPMD"));
    }
  }

  private static String annotationName(final String source, final int start) {
    int index = start;
    while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
      index++;
    }
    final StringBuilder name = new StringBuilder();
    while (index < source.length()) {
      final char current = source.charAt(index);
      if (Character.isJavaIdentifierPart(current) || current == '.') {
        name.append(current);
        index++;
      } else {
        break;
      }
    }
    return name.toString();
  }

  private static int stringEnd(final String source, final int start, final char quote) {
    int index = start + ONE;
    while (index < source.length()) {
      if (source.charAt(index) == BACKSLASH) {
        index += TWO;
      } else if (source.charAt(index) == quote) {
        return index + 1;
      } else {
        index++;
      }
    }
    return source.length();
  }

  private static int lineCommentEnd(final String source, final int start) {
    final int newline = source.indexOf('\n', start);
    return newline < 0 ? source.length() : newline;
  }

  private static int blockCommentEnd(final String source, final int start) {
    final int end = source.indexOf("*/", start + TWO);
    return end < 0 ? source.length() : end + TWO;
  }

  private static int textBlockEnd(final String source, final int start) {
    final int end = source.indexOf("\"\"\"", start + THREE);
    return end < 0 ? source.length() : end + THREE;
  }

  private static int lines(final String source, final int start, final int end) {
    return (int)
        source.substring(start, end).chars().filter(character -> character == NEWLINE).count();
  }

  record Diagnostic(int line, String mechanism) {}

  private record Position(int index, int line) {}
}
