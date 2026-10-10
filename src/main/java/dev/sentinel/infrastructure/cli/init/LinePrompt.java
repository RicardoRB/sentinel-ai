package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.config.SentinelException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads line-based answers and numbered selections for the init wizard. */
final class LinePrompt {
  private static final String INPUT_ENDED =
      "Initialization cancelled: input ended before setup completed.";

  private final BufferedReader reader;

  LinePrompt(final BufferedReader reader) {
    this.reader = reader;
  }

  /** Returns the next line, or empty when input ended. */
  Optional<String> readLine(final String subject) {
    try {
      return Optional.ofNullable(reader.readLine());
    } catch (IOException e) {
      throw new SentinelException("Could not read " + subject + ": " + e.getMessage(), e);
    }
  }

  /** Returns the next line, cancelling initialization when input ended. */
  String requireLine(final String subject) {
    return readLine(subject).orElseThrow(() -> new SentinelException(INPUT_ENDED));
  }

  /** Returns whether the next line answers yes; unreadable input counts as no. */
  boolean confirm() {
    try {
      final String answer = reader.readLine();
      return answer != null && "y".equalsIgnoreCase(answer.trim());
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Reads space- or comma-separated 1-based numbers and returns the matching ids, appended to the
   * initial ids without duplicates.
   */
  List<String> readSelection(
      final List<String> ids,
      final List<String> initial,
      final String label,
      final String emptySelectionMessage) {
    final String value = requireLine("initialization selection");
    final List<String> selected = new ArrayList<>(initial);
    for (final String token : value.trim().split("[ ,]+")) {
      if (!token.isBlank()) {
        final String id = ids.get(choice(token, ids.size(), label) - 1);
        if (!selected.contains(id)) {
          selected.add(id);
        }
      }
    }
    if (selected.isEmpty()) {
      throw new SentinelException(emptySelectionMessage);
    }
    return selected;
  }

  private static int choice(final String token, final int size, final String label) {
    final int choice;
    try {
      choice = Integer.parseInt(token);
    } catch (NumberFormatException e) {
      throw invalidChoice(token, size, label, e);
    }
    if (choice <= 0 || choice > size) {
      throw invalidChoice(token, size, label, null);
    }
    return choice;
  }

  private static SentinelException invalidChoice(
      final String token, final int size, final String label, final Throwable cause) {
    return new SentinelException(
        "Invalid " + label + " selection '" + token + "'. Choose numbers from 1 to " + size + ".",
        cause);
  }
}
