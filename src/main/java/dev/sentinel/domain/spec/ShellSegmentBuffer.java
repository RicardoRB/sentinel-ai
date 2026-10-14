package dev.sentinel.domain.spec;

import java.util.ArrayList;
import java.util.List;

/** Collects the words of the current simple command and routes redirection targets. */
final class ShellSegmentBuffer {

  /** What the next word means after a redirection operator. */
  enum Pending {
    NONE,
    WRITE,
    WRITE_OR_DESCRIPTOR,
    SKIP,
    HEREDOC,
    HEREDOC_TABS
  }

  private final List<ShellSegment> segments = new ArrayList<>();
  private final List<ShellWord> words = new ArrayList<>();
  private final List<ShellWord> writes = new ArrayList<>();
  private final List<String> heredocs = new ArrayList<>();
  private Pending pending = Pending.NONE;

  void expect(final Pending next) {
    pending = next;
  }

  void accept(final ShellWord word) {
    switch (pending) {
      case NONE -> words.add(word);
      case WRITE -> writes.add(word);
      case WRITE_OR_DESCRIPTOR -> {
        if (!word.text().matches("\\d+|-")) {
          writes.add(word);
        }
      }
      case HEREDOC -> heredocs.add(word.text());
      case HEREDOC_TABS -> heredocs.add("\t" + word.text());
      case SKIP -> {
        // An input source is only read.
      }
    }
    pending = Pending.NONE;
  }

  void endSegment() {
    pending = Pending.NONE;
    if (!words.isEmpty() || !writes.isEmpty()) {
      segments.add(new ShellSegment(words, writes));
    }
    words.clear();
    writes.clear();
  }

  /**
   * Returns and forgets the pending here-document delimiters. A leading tab marks a {@code <<-}
   * delimiter whose body lines may be indented with tabs.
   */
  List<String> takeHeredocs() {
    final List<String> delimiters = List.copyOf(heredocs);
    heredocs.clear();
    return delimiters;
  }

  List<ShellSegment> segments() {
    return List.copyOf(segments);
  }
}
