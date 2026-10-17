package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.domain.config.SentinelException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads line-based answers and numbered selections for the init wizard. */
final class LinePrompt {
  private static final Logger LOGGER = Logger.getLogger(LinePrompt.class.getName());
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

  /** Returns whether the next line answers yes; end of input selects the default of no. */
  boolean confirm(final String question) {
    return readOptionalAnswer(question)
        .map(answer -> "y".equalsIgnoreCase(answer.trim()))
        .orElse(false);
  }

  boolean confirmInstallation() {
    final String answer = requireLine("OpenSpec installation consent");
    return "y".equalsIgnoreCase(answer.trim()) || "yes".equalsIgnoreCase(answer.trim());
  }

  /**
   * Reads space- or comma-separated 1-based numbers that toggle items of the initial selection. A
   * blank line accepts the initial selection. Kept initial ids come first, followed by ids turned
   * on in the order they were entered.
   */
  List<String> readSelection(
      final List<String> ids,
      final List<String> initial,
      final String label,
      final String emptySelectionMessage) {
    final String value = requireLine("initialization selection");
    final List<String> selected = new ArrayList<>(initial.stream().filter(ids::contains).toList());
    for (final String token : value.trim().split("[ ,]+")) {
      if (!token.isBlank()) {
        final String id = ids.get(choice(token, ids.size(), label) - 1);
        if (!selected.remove(id)) {
          selected.add(id);
        }
      }
    }
    if (selected.isEmpty()) {
      throw new SentinelException(emptySelectionMessage);
    }
    return List.copyOf(selected);
  }

  /** Reads an optional selection; a blank line or end of input selects nothing. */
  List<String> readOptionalSelection(
      final List<String> ids, final String label, final String question) {
    final String value = readOptionalAnswer(question).orElse("");
    if (value.isBlank()) {
      return List.of();
    }
    return readSelectionValue(ids, value, label);
  }

  private Optional<String> readOptionalAnswer(final String question) {
    final Optional<String> answer = readLine(question);
    if (answer.isEmpty()) {
      LOGGER.log(
          Level.INFO,
          () -> "event=init-optional-default question=" + question + " reason=end-of-input");
    }
    return answer;
  }

  private List<String> readSelectionValue(
      final List<String> ids, final String value, final String label) {
    final List<String> selected = new ArrayList<>();
    for (final String token : value.trim().split("[ ,]+")) {
      if (!token.isBlank()) {
        final String id = ids.get(choice(token, ids.size(), label) - 1);
        if (!selected.contains(id)) {
          selected.add(id);
        }
      }
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
