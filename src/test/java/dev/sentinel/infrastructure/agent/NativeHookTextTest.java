package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import org.junit.jupiter.api.Test;

class NativeHookTextTest {
  @Test
  void quotedStringsEscapeControlCharactersSoResponsesStayValidJson() {
    final String reason = "line one\nline \"two\"\r\n\ttab\\ and \u0001 control";
    final String json = NativeHookText.quote(reason);

    assertThat(json).doesNotContain("\n", "\r", "\t", "\u0001");
    assertThat(
            new ForyJsonCodec()
                .fromJson("{\"command\":" + json + "}", CodexIntegration.ToolInput.class)
                .command)
        .isEqualTo(reason);
  }

  @Test
  void multiLineDenialsSerializeToValidJson() {
    final String deny =
        new CodexIntegration().serialize(NativeHookOutcome.deny("Blocked.\n- first reason"));

    assertThat(deny).doesNotContain("\n");
    assertThat(deny).contains("Blocked.\\n- first reason");
  }
}
