package dev.sentinel.infrastructure.cli.init;

import java.io.PrintWriter;
import java.util.function.Supplier;

/** Asks a question again after an invalid answer when a person is at the terminal. */
final class Answering {
  private final boolean retry;
  private final PrintWriter out;

  Answering(final boolean retry, final PrintWriter out) {
    this.retry = retry;
    this.out = out;
  }

  /**
   * Returns the question's answer. On a terminal, an invalid answer prints its error and asks
   * again; otherwise it fails. Input that ended is never retried.
   */
  <T> T ask(final Supplier<T> question) {
    while (true) {
      try {
        return question.get();
      } catch (InvalidAnswerException invalid) {
        if (!retry) {
          throw invalid;
        }
        out.println(invalid.getMessage());
      }
    }
  }
}
