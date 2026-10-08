package dev.sentinel.infrastructure.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PosixRawTerminalTest {
  @Test
  void unsupportedOperatingSystemFallsBack() {
    assertThat(new PosixRawTerminal("Windows 11").open()).isEmpty();
  }

  @Test
  void rejectsUnsupportedArchitecture() {
    final String operatingSystem = System.getProperty("os.name", "Linux");
    final String architecture = System.getProperty("os.arch");
    try {
      System.setProperty("os.arch", "sparc");
      assertThat(new PosixRawTerminal(operatingSystem).open()).isEmpty();
    } finally {
      System.setProperty("os.arch", architecture);
    }
  }

  @Test
  void nonTtyStdinFallsBack() {
    assertThat(new PosixRawTerminal("Linux").open()).isEmpty();
  }
}
