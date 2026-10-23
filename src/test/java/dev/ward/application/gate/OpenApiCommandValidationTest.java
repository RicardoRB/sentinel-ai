package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenApiCommandValidationTest {
  private static final String LINT = "openapi-lint";
  private static final String DIFF = "openapi-diff";
  private static final String VACUUM = "vacuum";
  private static final String OASDIFF = "oasdiff";

  private static List<String> lint(final String... flags) {
    return command(VACUUM, "lint", flags);
  }

  private static List<String> command(
      final String executable, final String subcommand, final String... flags) {
    return Stream.concat(Stream.of(executable, subcommand), Arrays.stream(flags)).toList();
  }

  @Test
  void acceptsStrictLintCommandsInEveryFlagForm() {
    assertThatCode(() -> OpenApiCommandValidation.require(LINT, lint("-z", "-n", "warn")))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                OpenApiCommandValidation.require(
                    LINT,
                    lint("--hard-mode", "--fail-severity=hint", "-b", "-q", "--min-score=80")))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"error", "none", "warning", "bogus", ""})
  void rejectsLoweredOrUnknownLintSeverity(final String severity) {
    assertThatThrownBy(() -> OpenApiCommandValidation.require(LINT, lint("-z", "-n", severity)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("requires --fail-severity warn, info, or hint");
    assertThatThrownBy(
            () -> OpenApiCommandValidation.require(LINT, lint("-z", "--fail-severity=" + severity)))
        .hasMessageContaining("requires --fail-severity warn, info, or hint");
  }

  @Test
  void rejectsLintWithoutSeverity() {
    assertThatThrownBy(() -> OpenApiCommandValidation.require(LINT, lint("-z")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("requires --fail-severity warn, info, or hint");
  }

  @Test
  void rejectsLintWithoutHardMode() {
    assertThatThrownBy(() -> OpenApiCommandValidation.require(LINT, lint("-n", "warn")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("requires -z (--hard-mode)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"-r", "--ruleset", "--ignore-file", "--config", "--fix", "-T"})
  void rejectsLintFlagsThatCanSilenceRules(final String flag) {
    assertThatThrownBy(
            () -> OpenApiCommandValidation.require(LINT, lint("-z", "-n", "warn", flag, "x.yaml")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("does not allow '" + flag + "'");
  }

  @Test
  void rejectsPositionalSpecAndRepeatedFlags() {
    assertThatThrownBy(
            () -> OpenApiCommandValidation.require(LINT, lint("-z", "-n", "warn", "openapi.yml")))
        .hasMessageContaining("does not allow 'openapi.yml'");
    assertThatThrownBy(
            () -> OpenApiCommandValidation.require(LINT, lint("-z", "-n", "warn", "-n", "hint")))
        .hasMessageContaining("more than once");
  }

  @Test
  void rejectsWrongSubcommand() {
    assertThatThrownBy(() -> OpenApiCommandValidation.require(LINT, List.of(VACUUM, "report")))
        .hasMessageContaining("must run the 'lint' subcommand");
    assertThatThrownBy(() -> OpenApiCommandValidation.require(DIFF, List.of(OASDIFF)))
        .hasMessageContaining("must run the 'breaking' subcommand");
  }
}
