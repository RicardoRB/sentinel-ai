package dev.sentinel.domain.spec;

/** Reads single- and double-quoted shell strings. */
final class ShellQuotes {
  private static final char DOUBLE = '"';
  private static final char ESCAPE = '\\';
  private static final char NEWLINE = '\n';
  private static final String DOUBLE_QUOTE_ESCAPES = "\"\\$`\n";
  private static final String EXPANSIONS = "$`";

  private ShellQuotes() {}

  /**
   * Adds the quoted string opening at {@code open} to {@code word} and returns the index after its
   * closing quote, or -1 when it is never closed.
   */
  static int scan(final String text, final int open, final ShellWordBuffer word) {
    final char quote = text.charAt(open);
    final StringBuilder content = new StringBuilder();
    int index = open + 1;
    while (index < text.length() && text.charAt(index) != quote) {
      index =
          quote == DOUBLE ? doubleQuoted(text, index, content, word) : append(text, index, content);
    }
    if (index >= text.length()) {
      return -1;
    }
    word.quoted(content);
    return index + 1;
  }

  private static int doubleQuoted(
      final String text, final int index, final StringBuilder content, final ShellWordBuffer word) {
    final char current = text.charAt(index);
    final int next =
        index + 1 < text.length() ? DOUBLE_QUOTE_ESCAPES.indexOf(text.charAt(index + 1)) : -1;
    if (current == ESCAPE && next >= 0) {
      final char escaped = DOUBLE_QUOTE_ESCAPES.charAt(next);
      if (escaped != NEWLINE) {
        content.append(escaped);
      }
      return index + 2;
    }
    if (EXPANSIONS.indexOf(current) >= 0) {
      word.markDynamic();
    }
    return append(text, index, content);
  }

  private static int append(final String text, final int index, final StringBuilder content) {
    content.append(text.charAt(index));
    return index + 1;
  }
}
