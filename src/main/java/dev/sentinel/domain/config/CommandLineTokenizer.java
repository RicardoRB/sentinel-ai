package dev.sentinel.domain.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a configured command string into an argument vector.
 *
 * <p>This is deliberately NOT a shell: it only understands whitespace separation and single/double
 * quotes. There is no expansion, piping, redirection, globbing or variable substitution. Use the
 * TOML array form for arguments that need exact control.
 */
public final class CommandLineTokenizer {

  private CommandLineTokenizer() {}

  public static List<String> tokenize(final String commandLine) {
    final List<String> tokens = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    boolean inToken = false;
    char quote = 0;
    for (final char c : commandLine.toCharArray()) {
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        } else {
          current.append(c);
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
        inToken = true;
      } else if (Character.isWhitespace(c)) {
        if (inToken) {
          tokens.add(current.toString());
          current.setLength(0);
          inToken = false;
        }
      } else {
        current.append(c);
        inToken = true;
      }
    }
    if (quote != 0) {
      throw new SentinelException("Unterminated quote in command: " + commandLine);
    }
    if (inToken) {
      tokens.add(current.toString());
    }
    return tokens;
  }
}
