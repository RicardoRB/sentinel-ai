package dev.ward.domain.spec;

import java.util.List;

/**
 * One simple command between shell operators.
 *
 * @param words the command and its arguments, without redirections
 * @param writes the targets of output redirections
 */
record ShellSegment(List<ShellWord> words, List<ShellWord> writes) {

  ShellSegment {
    words = List.copyOf(words);
    writes = List.copyOf(writes);
  }
}
