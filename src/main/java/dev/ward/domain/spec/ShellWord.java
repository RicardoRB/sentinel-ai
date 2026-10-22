package dev.ward.domain.spec;

/**
 * One shell word after quote removal.
 *
 * @param glob whether it contains an unquoted pattern character
 * @param dynamic whether it contains an expansion that cannot be resolved without running a shell
 */
record ShellWord(String text, boolean glob, boolean dynamic) {

  /** A word with the same pattern and expansion flags but different text. */
  ShellWord withText(final String replacement) {
    return new ShellWord(replacement, glob, dynamic);
  }
}
