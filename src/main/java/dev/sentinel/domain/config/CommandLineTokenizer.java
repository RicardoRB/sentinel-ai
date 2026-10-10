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
    final Tokens tokens = new Tokens();
    final StringBuilder current = new StringBuilder();
    for (final char c : commandLine.toCharArray()) {
      tokens.accept(c, current);
    }
    if (tokens.inQuote()) {
      throw new SentinelException("Unterminated quote in command: " + commandLine);
    }
    return tokens.finish(current);
  }

  /** Tracks quote and token state for one {@link #tokenize} call. */
  private static final class Tokens {
    private static final char NO_QUOTE = 0;

    private final List<String> values = new ArrayList<>();
    private boolean inToken;
    private char quote = NO_QUOTE;

    void accept(final char c, final StringBuilder current) {
      if (quote == NO_QUOTE) {
        acceptUnquoted(c, current);
      } else if (c == quote) {
        quote = NO_QUOTE;
      } else {
        current.append(c);
      }
    }

    boolean inQuote() {
      return quote != NO_QUOTE;
    }

    List<String> finish(final StringBuilder current) {
      endToken(current);
      return values;
    }

    private void acceptUnquoted(final char c, final StringBuilder current) {
      switch (c) {
        case '"', '\'' -> {
          quote = c;
          inToken = true;
        }
        default -> {
          if (Character.isWhitespace(c)) {
            endToken(current);
          } else {
            current.append(c);
            inToken = true;
          }
        }
      }
    }

    private void endToken(final StringBuilder current) {
      if (inToken) {
        values.add(current.toString());
        current.setLength(0);
        inToken = false;
      }
    }
  }
}
