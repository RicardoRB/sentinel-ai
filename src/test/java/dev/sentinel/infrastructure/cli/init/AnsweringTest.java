package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnsweringTest {
  private final StringWriter output = new StringWriter();

  @Test
  void terminalAsksAgainAfterAnInvalidAnswer() {
    final Iterator<String> answers = List.of("4", "1").iterator();

    final String answer = new Answering(true, writer()).ask(() -> parse(answers.next()));

    assertThat(answer).isEqualTo("1");
    assertThat(output.toString()).contains("Invalid choice '4'");
  }

  @Test
  void pipedInputFailsOnAnInvalidAnswer() {
    final Answering answering = new Answering(false, writer());

    assertThatThrownBy(() -> answering.ask(() -> parse("4")))
        .isInstanceOf(InvalidAnswerException.class)
        .hasMessage("Invalid choice '4'");
  }

  @Test
  void inputThatEndedIsNeverRetried() {
    final Answering answering = new Answering(true, writer());

    assertThatThrownBy(
            () ->
                answering.ask(
                    () -> {
                      throw new SentinelException("input ended");
                    }))
        .isNotInstanceOf(InvalidAnswerException.class)
        .hasMessage("input ended");
  }

  private PrintWriter writer() {
    return new PrintWriter(output, true);
  }

  private static String parse(final String value) {
    if (!"1".equals(value)) {
      throw new InvalidAnswerException("Invalid choice '" + value + "'");
    }
    return value;
  }
}
