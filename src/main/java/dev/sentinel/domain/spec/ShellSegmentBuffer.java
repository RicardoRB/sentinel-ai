package dev.sentinel.domain.spec;

import java.util.ArrayList;
import java.util.List;

/** Collects the words of the current simple command and routes redirection targets. */
final class ShellSegmentBuffer {

  /** What the next word means after a redirection operator. */
  enum Pending {
    NONE {
      @Override
      void route(final ShellSegmentBuffer buffer, final ShellWord word) {
        buffer.words.add(word);
      }
    },
    WRITE {
      @Override
      void route(final ShellSegmentBuffer buffer, final ShellWord word) {
        buffer.writes.add(word);
      }
    },
    WRITE_OR_DESCRIPTOR {
      @Override
      void route(final ShellSegmentBuffer buffer, final ShellWord word) {
        if (!word.text().matches("\\d+|-")) {
          buffer.writes.add(word);
        }
      }
    },
    SKIP {
      @Override
      void route(final ShellSegmentBuffer buffer, final ShellWord word) {
        // An input source is only read.
      }
    },
    HEREDOC {
      @Override
      void route(final ShellSegmentBuffer buffer, final ShellWord word) {
        buffer.heredocs.add(word.text());
      }
    },
    HEREDOC_TABS {
      @Override
      void route(final ShellSegmentBuffer buffer, final ShellWord word) {
        buffer.heredocs.add("\t" + word.text());
      }
    };

    /** Routes {@code word} into {@code buffer} as this redirection state requires. */
    abstract void route(ShellSegmentBuffer buffer, ShellWord word);
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
    pending.route(this, word);
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
