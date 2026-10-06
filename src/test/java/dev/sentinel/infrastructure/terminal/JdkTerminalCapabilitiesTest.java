package dev.sentinel.infrastructure.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdkTerminalCapabilitiesTest {
  @Test
  void interactiveConsoleIsDisabledByNoColorOrDumbTerm() {
    assertThat(new JdkTerminalCapabilities(true, null, "xterm-256color").interactive()).isTrue();
    assertThat(new JdkTerminalCapabilities(true, "1", "xterm").interactive()).isFalse();
    assertThat(new JdkTerminalCapabilities(true, null, "dumb").interactive()).isFalse();
    assertThat(new JdkTerminalCapabilities(false, null, "xterm").interactive()).isFalse();
  }

  @Test
  void colorRequiresUsableInteractiveTerminal() {
    assertThat(new JdkTerminalCapabilities(true, null, "xterm").colorEnabled()).isTrue();
    assertThat(new JdkTerminalCapabilities(true, "", "xterm").colorEnabled()).isFalse();
  }
}
