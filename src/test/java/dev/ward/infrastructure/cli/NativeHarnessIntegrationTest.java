package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisabledOnOs(OS.WINDOWS)
class NativeHarnessIntegrationTest extends WardCliIntegrationSupport {
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
        Arguments.of("codex", ".codex/hooks.json", ".codex/hooks/ward.sh"),
        Arguments.of("cursor", ".cursor/hooks.json", ".cursor/hooks/ward.sh"),
        Arguments.of("copilot", ".github/hooks/ward.json", ".github/hooks/ward.sh"),
        Arguments.of("gemini", ".gemini/settings.json", ".gemini/hooks/ward.sh"),
        Arguments.of("windsurf", ".windsurf/hooks.json", ".windsurf/hooks/ward.sh"),
        Arguments.of("cline", ".cline/hooks/PostToolUse", ".cline/hooks/PreToolUse"),
        Arguments.of("factory-droid", ".factory/hooks.json", ".factory/hooks/ward.sh"),
        Arguments.of("amp", ".amp/plugins/ward.ts", ".amp/plugins/ward.sh"));
  }
}
