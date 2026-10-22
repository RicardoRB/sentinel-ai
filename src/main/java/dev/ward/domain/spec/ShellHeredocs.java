package dev.ward.domain.spec;

import java.util.List;

/** Skips here-document bodies so their text is not mistaken for commands. */
final class ShellHeredocs {
  private static final char NEWLINE = '\n';
  private static final String TAB = "\t";

  private ShellHeredocs() {}

  /**
   * Returns the index after the bodies of {@code delimiters}, which start at {@code position}. A
   * leading tab marks a {@code <<-} delimiter whose closing line may be indented with tabs.
   */
  static int skip(final String text, final int position, final List<String> delimiters) {
    int index = position;
    for (final String delimiter : delimiters) {
      index = skipBody(text, index, delimiter);
    }
    return index;
  }

  private static int skipBody(final String text, final int start, final String delimiter) {
    final boolean tabs = delimiter.startsWith(TAB);
    final String name = tabs ? delimiter.substring(1) : delimiter;
    int index = start;
    while (index < text.length()) {
      final int end = text.indexOf(NEWLINE, index);
      final String line = text.substring(index, end < 0 ? text.length() : end);
      index = end < 0 ? text.length() : end + 1;
      if (name.equals(tabs ? line.replaceFirst("^\t+", "") : line)) {
        return index;
      }
    }
    return index;
  }
}
