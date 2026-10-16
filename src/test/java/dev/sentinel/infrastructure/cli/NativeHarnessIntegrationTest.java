package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisabledOnOs(OS.WINDOWS)
class NativeHarnessIntegrationTest extends SentinelCliIntegrationSupport {
  @ParameterizedTest
  @MethodSource("harnesses")
  void installsAndRemovesEachNativeHarness(
      final String id, final String config, final String hook) {
    assertThat(run("integrate", id, "-C", project.toString())).isZero();
    assertThat(project.resolve(config)).exists();
    assertThat(project.resolve(hook)).exists();
    assertThat(run("integrate", id, "--remove", "-C", project.toString())).isZero();
    assertThat(project.resolve(config)).doesNotExist();
    assertThat(project.resolve(hook)).doesNotExist();
  }

  private static Stream<Arguments> harnesses() {
    return Stream.of(
        Arguments.of("codex", ".codex/hooks.json", ".codex/hooks/sentinel.sh"),
        Arguments.of("cursor", ".cursor/hooks.json", ".cursor/hooks/sentinel.sh"),
        Arguments.of("copilot", ".github/hooks/sentinel.json", ".github/hooks/sentinel.sh"),
        Arguments.of("gemini", ".gemini/settings.json", ".gemini/hooks/sentinel.sh"),
        Arguments.of("windsurf", ".windsurf/hooks.json", ".windsurf/hooks/sentinel.sh"),
        Arguments.of("cline", ".cline/hooks/PostToolUse", ".cline/hooks/PreToolUse"),
        Arguments.of("factory-droid", ".factory/hooks.json", ".factory/hooks/sentinel.sh"),
        Arguments.of("amp", ".amp/plugins/sentinel.ts", ".amp/plugins/sentinel.sh"));
  }
}
