package dev.sentinel.infrastructure.cli.logging;

import java.util.StringJoiner;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Compact formatter for application diagnostics. It deliberately never renders exception messages.
 */
public final class SafeLogFormatter extends Formatter {

  @Override
  public String format(final LogRecord record) {
    String result =
        "["
            + record.getLevel().getName()
            + "] "
            + escape(record.getLoggerName())
            + ": "
            + escape(record.getMessage());
    if (record.getThrown() != null) {
      result += formatThrowable(record.getThrown());
    }
    return result + System.lineSeparator();
  }

  private static String formatThrowable(final Throwable throwable) {
    final StringJoiner result = new StringJoiner(" ", " ", "");
    result.add("exception=" + throwable.getClass().getName());
    final StackTraceElement[] frames = throwable.getStackTrace();
    if (frames.length > 0) {
      result.add("at=" + escape(frames[0].toString()));
    }
    if (throwable.getCause() != null) {
      result.add("cause=" + throwable.getCause().getClass().getName());
    }
    return result.toString();
  }

  static String escape(final String value) {
    if (value == null) {
      return "";
    }
    final StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      switch (character) {
        case '\\' -> escaped.append("\\\\");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (Character.isISOControl(character)) {
            escaped.append(String.format("\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
