package dev.sentinel.domain.spec;

import java.util.List;
import java.util.Optional;

/**
 * Splits shell command text into simple commands without executing or expanding anything. It honors
 * quotes, escapes, operators, redirections, comments, and here-documents. Command substitutions
 * become commands of their own.
 */
final class ShellLexer {
  private static final char NEWLINE = '\n';
  private static final char ESCAPE = '\\';
  private static final char DOLLAR = '$';
  private static final char COMMENT = '#';
  private static final char OPEN = '(';
  private static final char AMPERSAND = '&';
  private static final char PIPE = '|';
  private static final char GREATER = '>';
  private static final char BACKTICK = '`';
  private static final String BLANKS = " \t";
  private static final String QUOTES = "'\"";
  private static final String REDIRECTS = "<>";
  private static final String OPERATORS = ";|&()`";

  private final String text;
  private final ShellWordBuffer word = new ShellWordBuffer();
  private final ShellSegmentBuffer segment = new ShellSegmentBuffer();
  private int position;

  private ShellLexer(final String text) {
    this.text = text;
  }

  /** Returns the simple commands, or empty when a quote is never closed. */
  static Optional<List<ShellSegment>> lex(final String text) {
    final ShellLexer lexer = new ShellLexer(text);
    while (lexer.position < text.length()) {
      if (!lexer.step(text.charAt(lexer.position))) {
        return Optional.empty();
      }
    }
    lexer.endWord();
    lexer.segment.endSegment();
    return Optional.of(lexer.segment.segments());
  }

  private boolean step(final char character) {
    if (character == NEWLINE) {
      endWord();
      segment.endSegment();
      position = ShellHeredocs.skip(text, position + 1, segment.takeHeredocs());
    } else if (QUOTES.indexOf(character) >= 0) {
      position = ShellQuotes.scan(text, position, word);
    } else if (character == COMMENT && !word.started()) {
      final int end = text.indexOf(NEWLINE, position);
      position = end < 0 ? text.length() : end;
    } else {
      consume(character);
      position++;
    }
    return position >= 0;
  }

  private void consume(final char character) {
    if (BLANKS.indexOf(character) >= 0) {
      endWord();
    } else if (character == ESCAPE) {
      escaped();
    } else if (character == DOLLAR) {
      dollar();
    } else if (REDIRECTS.indexOf(character) >= 0) {
      redirect();
    } else if (OPERATORS.indexOf(character) >= 0) {
      operator(character);
    } else {
      word.literal(character);
    }
  }

  private void escaped() {
    if (position + 1 < text.length()) {
      final char next = text.charAt(position + 1);
      if (next != NEWLINE) {
        word.quoted(String.valueOf(next));
      }
      position++;
    }
  }

  private void dollar() {
    if (next() == OPEN) {
      endWord();
      segment.endSegment();
      position++;
    } else {
      word.literal(DOLLAR);
      word.markDynamic();
    }
  }

  private void redirect() {
    word.discardDescriptor();
    endWord();
    position += ShellRedirects.parse(text, position, segment);
  }

  private void operator(final char operator) {
    endWord();
    if (operator == AMPERSAND && next() == GREATER) {
      position += ShellRedirects.parse(text, position, segment);
      return;
    }
    segment.endSegment();
    final char next = next();
    if (operator != BACKTICK && (next == operator || operator == PIPE && next == AMPERSAND)) {
      position++;
    }
  }

  private void endWord() {
    if (word.started()) {
      segment.accept(word.take());
    }
  }

  private char next() {
    return position + 1 < text.length() ? text.charAt(position + 1) : '\0';
  }
}
