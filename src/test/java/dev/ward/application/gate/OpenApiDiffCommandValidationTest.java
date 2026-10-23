package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenApiDiffCommandValidationTest {
  private static final String LINT = "openapi-lint";
  private static final String DIFF = "openapi-diff";
  private static final String COMPAT = "openapi-compat";
  private static final String VACUUM = "vacuum";
  private static final String OASDIFF = "oasdiff";

  private static List<String> lint(final String... flags) {
    return command(VACUUM, "lint", flags);
  }

  private static List<String> diff(final String... flags) {
    return command(OASDIFF, "breaking", flags);
  }

  private static List<String> command(
      final String executable, final String subcommand, final String... flags) {
    return Stream.concat(Stream.of(executable, subcommand), Arrays.stream(flags)).toList();
  }

  @Test
  void acceptsStrictDiffCommands() {
    assertThatCode(() -> OpenApiCommandValidation.require(DIFF, diff("--fail-on", "ERR")))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                OpenApiCommandValidation.require(
                    COMPAT, diff("-o", "WARN", "-f", "githubactions", "--color=never")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDiffWithoutFailureThreshold() {
    assertThatThrownBy(() -> OpenApiCommandValidation.require(DIFF, diff()))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("requires --fail-on ERR or --fail-on WARN");
    assertThatThrownBy(() -> OpenApiCommandValidation.require(COMPAT, diff("--fail-on", "INFO")))
        .hasMessageContaining("requires --fail-on ERR or --fail-on WARN");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "--err-ignore",
        "--warn-ignore",
        "--match-path",
        "--unmatch-path",
        "--filter-extension",
        "--severity-levels",
        "--config",
        "--open",
        "--fetch"
      })
  void rejectsDiffFilterIgnoreAndUploadFlags(final String flag) {
    assertThatThrownBy(
            () -> OpenApiCommandValidation.require(COMPAT, diff("--fail-on", "ERR", flag, "x")))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("does not allow '" + flag + "'");
  }

  @Test
  void factoryRejectsInvalidCommandWithConfigurationError() {
    final Map<String, GateConfiguration> gates =
        Map.of(
            LINT,
            new GateConfiguration(
                true,
                lint("-n", "warn"),
                Set.of("default"),
                null,
                null,
                new OpenApiGateOptions("openapi.yml", null)));
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(new WardConfiguration(1, gates)))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("--hard-mode");
  }
}
