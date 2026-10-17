package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinePromptTest {
  private static final List<String> IDS = List.of("tests", "compile", "pmd", "sonar");
  private static final List<String> PRESET = List.of("tests", "compile", "pmd");
  private static final String EMPTY = "Select at least one quality gate.";

  @Test
  void numberOfPreselectedItemTogglesItOff() {
    assertThat(prompt("3\n").readSelection(IDS, PRESET, "quality-gate", EMPTY))
        .containsExactly("tests", "compile");
  }

  @Test
  void numberOfUnselectedItemTogglesItOn() {
    assertThat(prompt("4\n").readSelection(IDS, PRESET, "quality-gate", EMPTY))
        .containsExactly("tests", "compile", "pmd", "sonar");
  }

  @Test
  void repeatedNumberTogglesBack() {
    assertThat(prompt("3 3\n").readSelection(IDS, PRESET, "quality-gate", EMPTY))
        .containsExactlyElementsOf(PRESET);
  }

  @Test
  void blankLineAcceptsPreselection() {
    assertThat(prompt("\n").readSelection(IDS, PRESET, "quality-gate", EMPTY))
        .containsExactlyElementsOf(PRESET);
  }

  @Test
  void blankLineWithoutPreselectionIsRejected() {
    assertThatThrownBy(() -> prompt("\n").readSelection(IDS, List.of(), "quality-gate", EMPTY))
        .isInstanceOf(SentinelException.class)
        .hasMessage(EMPTY);
  }

  @Test
  void togglingEverythingOffIsRejected() {
    assertThatThrownBy(() -> prompt("1 2 3\n").readSelection(IDS, PRESET, "quality-gate", EMPTY))
        .isInstanceOf(SentinelException.class)
        .hasMessage(EMPTY);
  }

  @Test
  void invalidNumberIsRejected() {
    assertThatThrownBy(() -> prompt("9\n").readSelection(IDS, PRESET, "quality-gate", EMPTY))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("Invalid quality-gate selection '9'");
  }

  private static LinePrompt prompt(final String input) {
    return new LinePrompt(new BufferedReader(new StringReader(input)));
  }
}
