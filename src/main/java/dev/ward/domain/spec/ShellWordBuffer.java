package dev.ward.domain.spec;

import java.util.ArrayList;
import java.util.List;

/** Accumulates the characters of the shell word being lexed. */
final class ShellWordBuffer {
  private static final String GLOB_CHARACTERS = "*?[{";
  private static final char HOME = '~';

  private final List<CharSequence> parts = new ArrayList<>();
  private boolean quoted;
  private boolean glob;
  private boolean dynamic;

  void literal(final char character) {
    if (parts.isEmpty() && character == HOME) {
      dynamic = true;
    }
    if (GLOB_CHARACTERS.indexOf(character) >= 0) {
      glob = true;
    }
    parts.add(String.valueOf(character));
  }

  void quoted(final CharSequence characters) {
    parts.add(characters);
    quoted = true;
  }

  /** Marks the word as depending on an expansion, such as a variable. */
  void markDynamic() {
    dynamic = true;
  }

  boolean started() {
    return !parts.isEmpty() || quoted;
  }

  /** Drops an unquoted all-digit word that is really a file-descriptor number, as in {@code 2>}. */
  void discardDescriptor() {
    if (!parts.isEmpty()
        && !quoted
        && String.join("", parts).chars().allMatch(Character::isDigit)) {
      reset();
    }
  }

  ShellWord take() {
    final ShellWord word = new ShellWord(String.join("", parts), glob, dynamic);
    reset();
    return word;
  }

  private void reset() {
    parts.clear();
    quoted = false;
    glob = false;
    dynamic = false;
  }
}
