package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinePromptTest {
  private static final List<String> IDS = List.of("tests", "compile", "pmd", "sonar");
  private static final List<String> PRESET = List.of("tests", "compile", "pmd");
  private static final String EMPTY = "Select at least one quality gate.";
  private static final String LABEL = "quality-gate";

  @Test
  void numberOfPreselectedItemTogglesItOff() {
    assertThat(prompt("3\n").readSelection(IDS, PRESET, LABEL, EMPTY).orElseThrow())
        .containsExactly("tests", "compile");
  }

  @Test
  void numberOfUnselectedItemTogglesItOn() {
    assertThat(prompt("4\n").readSelection(IDS, PRESET, LABEL, EMPTY).orElseThrow())
        .containsExactly("tests", "compile", "pmd", "sonar");
  }

  @Test
  void repeatedNumberTogglesBack() {
    assertThat(prompt("3 3\n").readSelection(IDS, PRESET, LABEL, EMPTY).orElseThrow())
        .containsExactlyElementsOf(PRESET);
  }

  @Test
  void blankLineAcceptsPreselection() {
    assertThat(prompt("\n").readSelection(IDS, PRESET, LABEL, EMPTY).orElseThrow())
        .containsExactlyElementsOf(PRESET);
  }

  @Test
  void blankLineWithoutPreselectionIsRejected() {
    assertThatThrownBy(() -> prompt("\n").readSelection(IDS, List.of(), LABEL, EMPTY))
        .isInstanceOf(WardException.class)
        .hasMessage(EMPTY);
  }

  @Test
  void togglingEverythingOffIsRejected() {
    assertThatThrownBy(() -> prompt("1 2 3\n").readSelection(IDS, PRESET, LABEL, EMPTY))
        .isInstanceOf(WardException.class)
        .hasMessage(EMPTY);
  }

  @Test
  void invalidNumberIsRejected() {
    assertThatThrownBy(() -> prompt("9\n").readSelection(IDS, PRESET, LABEL, EMPTY))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Invalid quality-gate selection '9'");
  }

  private static LinePrompt prompt(final String input) {
    return new LinePrompt(new BufferedReader(new StringReader(input)));
  }

  @Test
  void endOfInputReturnsNoSelection() {
    assertThat(prompt("").readSelection(IDS, PRESET, LABEL, EMPTY)).isEmpty();
  }
}
