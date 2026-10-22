package dev.ward.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSpecOutputTest {
  // Captured from OpenSpec 1.14.1: findings appear only on stderr.
  private static final String INVALID_CHANGE_STDERR =
      """
      Change 'bad' has issues
      ⚠ [WARNING] x/spec.md: ADDED "Thing" should contain SHALL or MUST (RFC 2119 best practice for English specs)
      ✗ [ERROR] x/spec.md: ADDED "Thing" must include at least one scenario
      Next steps:
        - Ensure change has deltas in specs/: use headers ## ADDED/MODIFIED/REMOVED/RENAMED Requirements
        - Each requirement MUST include at least one #### Scenario: block
        - Debug parsed deltas: openspec show bad --json --deltas-only
      """;

  @Test
  void findingsOnlyOnStderrAreRecognizedAndOrderedBySeverity() {
    final List<SpecFinding> findings = OpenSpecOutput.parse("", INVALID_CHANGE_STDERR, false);

    assertThat(findings)
        .containsExactly(
            new SpecFinding(
                SpecFinding.Level.ERROR,
                "x/spec.md",
                "ADDED \"Thing\" must include at least one scenario"),
            new SpecFinding(
                SpecFinding.Level.WARNING,
                "x/spec.md",
                "ADDED \"Thing\" should contain SHALL or MUST (RFC 2119 best practice for English"
                    + " specs)"));
  }

  @Test
  void findingsOnStdoutAreRecognizedTooAndInfoSortsLast() {
    final List<SpecFinding> findings =
        OpenSpecOutput.parse("[INFO] note\n", "[WARNING] overview: Purpose is brief\n", false);

    assertThat(findings)
        .extracting(SpecFinding::level)
        .containsExactly(SpecFinding.Level.WARNING, SpecFinding.Level.INFO);
    assertThat(findings.get(1).location()).isNull();
    assertThat(findings.get(1).message()).isEqualTo("note");
  }

  @Test
  void validRunHasNoFindings() {
    assertThat(OpenSpecOutput.parse("Change 'foo' is valid\n", "", true)).isEmpty();
  }

  @Test
  void unrecognizedFailureYieldsBoundedExcerpt() {
    final List<SpecFinding> findings =
        OpenSpecOutput.parse("", "Unknown item 'nope'. Did you mean: bad?\n", false);

    assertThat(findings)
        .containsExactly(
            new SpecFinding(
                SpecFinding.Level.ERROR, null, "Unknown item 'nope'. Did you mean: bad?"));
  }

  @Test
  void excerptStopsAtNextStepsAndLineAndCharacterLimits() {
    final String manyLines = "line\n".repeat(50);
    assertThat(OpenSpecOutput.parse(manyLines, null, false).getFirst().message().lines())
        .hasSize(OpenSpecOutput.EXCERPT_LINES);

    final String longLine = "x".repeat(5_000);
    assertThat(OpenSpecOutput.parse(longLine, "", false).getFirst().message())
        .hasSize(OpenSpecOutput.EXCERPT_CHARACTERS + "...".length());

    assertThat(
            OpenSpecOutput.parse("boom\nNext steps:\n  - hint\n", "", false).getFirst().message())
        .isEqualTo("boom");
    assertThat(OpenSpecOutput.parse("", "", false).getFirst().message())
        .isEqualTo("Validation failed without output.");
  }
}
