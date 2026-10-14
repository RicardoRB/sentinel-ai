package dev.sentinel.domain.spec;

import java.util.Map;

/** Recognizes redirection operators and what the word after each one means. */
final class ShellRedirects {
  private static final char OPEN = '(';
  private static final int LONGEST = 3;
  private static final Map<String, ShellSegmentBuffer.Pending> OPERATORS =
      Map.ofEntries(
          Map.entry(">", ShellSegmentBuffer.Pending.WRITE),
          Map.entry(">>", ShellSegmentBuffer.Pending.WRITE),
          Map.entry(">|", ShellSegmentBuffer.Pending.WRITE),
          Map.entry(">&", ShellSegmentBuffer.Pending.WRITE_OR_DESCRIPTOR),
          Map.entry("&>", ShellSegmentBuffer.Pending.WRITE),
          Map.entry("&>>", ShellSegmentBuffer.Pending.WRITE),
          Map.entry("<>", ShellSegmentBuffer.Pending.WRITE),
          Map.entry("<", ShellSegmentBuffer.Pending.SKIP),
          Map.entry("<&", ShellSegmentBuffer.Pending.SKIP),
          Map.entry("<<<", ShellSegmentBuffer.Pending.SKIP),
          Map.entry("<<", ShellSegmentBuffer.Pending.HEREDOC),
          Map.entry("<<-", ShellSegmentBuffer.Pending.HEREDOC_TABS));

  private ShellRedirects() {}

  /**
   * Applies the longest redirection operator starting at {@code position} and returns how many
   * characters after the first one it used. A process substitution such as {@code <(cmd)} starts a
   * command of its own.
   */
  static int parse(final String text, final int position, final ShellSegmentBuffer segment) {
    if (position + 1 < text.length() && text.charAt(position + 1) == OPEN) {
      segment.endSegment();
      return 1;
    }
    for (int length = LONGEST; length > 0; length--) {
      final int end = Math.min(position + length, text.length());
      final ShellSegmentBuffer.Pending pending = OPERATORS.get(text.substring(position, end));
      if (pending != null) {
        segment.expect(pending);
        return end - position - 1;
      }
    }
    return 0;
  }
}
