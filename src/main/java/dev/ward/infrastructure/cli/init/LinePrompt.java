package dev.ward.infrastructure.cli.init;

import dev.ward.domain.config.WardException;
import java.io.BufferedReader;
import java.io.IOException;
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
      throw new WardException("Could not read " + subject + ": " + e.getMessage(), e);
    }
  }

  /** Returns the next line, cancelling initialization when input ended. */
  String requireLine(final String subject) {
    return readLine(subject).orElseThrow(() -> new WardException(INPUT_ENDED));
  }

  /** Returns the next line; when input ended, logs that {@code question} takes its default. */
  Optional<String> readOrDefault(final String question) {
    final Optional<String> answer = readLine(question);
    if (answer.isEmpty()) {
      logDefault(question);
    }
    return answer;
  }

  /** Returns whether the next line answers yes; end of input selects the default of no. */
  boolean confirm(final String question) {
    return readOrDefault(question).map(answer -> "y".equalsIgnoreCase(answer.trim())).orElse(false);
  }

  boolean confirmInstallation() {
    final String answer = requireLine("OpenSpec installation consent");
    return "y".equalsIgnoreCase(answer.trim()) || "yes".equalsIgnoreCase(answer.trim());
  }

  /**
   * Reads numbers that toggle items of the initial selection; a blank line accepts it. Returns
   * empty when input ended, so the caller decides between a default and an error.
   */
  Optional<List<String>> readSelection(
      final List<String> ids,
      final List<String> initial,
      final String label,
      final String emptySelectionMessage) {
    final Optional<String> line = readLine("initialization selection");
    if (line.isEmpty()) {
      return Optional.empty();
    }
    final List<String> selected = NumberedChoice.toggle(ids, initial, line.get(), label);
    if (selected.isEmpty()) {
      throw new InvalidAnswerException(emptySelectionMessage);
    }
    return Optional.of(selected);
  }

  /** Reads an optional selection; a blank line or end of input selects nothing. */
  List<String> readOptionalSelection(
      final List<String> ids, final String label, final String question) {
    return NumberedChoice.toggle(ids, List.of(), readOrDefault(question).orElse(""), label);
  }

  static void logDefault(final String question) {
    LOGGER.log(
        Level.INFO,
        () -> "event=init-optional-default question=" + question + " reason=end-of-input");
  }
}
