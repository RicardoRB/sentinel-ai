package dev.ward.infrastructure.cli.init;

import java.io.PrintWriter;

/**
 * Introduces each wizard question with {@code Step N of M}. M counts the questions known to be
 * asked so far and grows when an answer adds a follow-up question.
 */
final class QuestionProgress {
  private final PrintWriter out;
  private int asked;
  private int known;

  QuestionProgress(final PrintWriter out) {
    this.out = out;
  }

  /** Adds questions that are now certain to be asked. */
  void expect(final int questions) {
    known += questions;
  }

  /** Starts the next question. */
  void step() {
    asked++;
    known = Math.max(known, asked);
    out.println("Step " + asked + " of " + known);
  }
}
