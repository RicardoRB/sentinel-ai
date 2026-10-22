package dev.ward.infrastructure.cli.init;

import java.util.ArrayList;
import java.util.List;

/** Parses numbered answers typed at the line-based init prompts. */
final class NumberedChoice {
  private NumberedChoice() {}

  /**
   * Applies space- or comma-separated 1-based numbers as toggles over {@code initial}. Kept initial
   * ids come first, followed by ids turned on in the order they were entered.
   */
  static List<String> toggle(
      final List<String> ids, final List<String> initial, final String line, final String label) {
    final List<String> selected = new ArrayList<>(initial.stream().filter(ids::contains).toList());
    for (final String token : line.trim().split("[ ,]+")) {
      if (!token.isBlank()) {
        final String id = ids.get(number(token, ids.size(), label) - 1);
        if (!selected.remove(id)) {
          selected.add(id);
        }
      }
    }
    return List.copyOf(selected);
  }

  /** Returns the 1-based number in {@code token}, rejecting anything outside 1..size. */
  static int number(final String token, final int size, final String label) {
    final int choice;
    try {
      choice = Integer.parseInt(token.trim());
    } catch (NumberFormatException e) {
      throw invalid(token, size, label, e);
    }
    if (choice <= 0 || choice > size) {
      throw invalid(token, size, label, null);
    }
    return choice;
  }

  private static InvalidAnswerException invalid(
      final String token, final int size, final String label, final Throwable cause) {
    return new InvalidAnswerException(
        "Invalid " + label + " selection '" + token + "'. Choose numbers from 1 to " + size + ".",
        cause);
  }
}
