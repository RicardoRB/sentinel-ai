package dev.sentinel.infrastructure.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void sessionReadsAndClosesIdempotently() {
    final AtomicInteger restorations = new AtomicInteger();
    try (PosixRawTerminal.Session session =
        new PosixRawTerminal.Session(
            Arena.ofConfined(),
            new byte[] {1, 2},
            (fileDescriptor, buffer, count) -> {
              buffer.set(ValueLayout.JAVA_BYTE, 0, (byte) 65);
              return 1;
            },
            (fileDescriptor, action, attributes) -> {
              restorations.incrementAndGet();
              return 0;
            })) {
      assertThat(session.read()).isEqualTo(65);
      session.close();
      session.close();
    }

    assertThat(restorations).hasValue(1);
  }

  @Test
  void shutdownRestoresAttributesAndMakesCloseANoOp() {
    final AtomicInteger restorations = new AtomicInteger();
    try (PosixRawTerminal.Session session =
        new PosixRawTerminal.Session(
            Arena.ofConfined(),
            new byte[] {1, 2},
            (fileDescriptor, buffer, count) -> -1,
            (fileDescriptor, action, attributes) -> {
              restorations.incrementAndGet();
              return 0;
            })) {
      session.restoreOnShutdown();
      session.close();
    }

    assertThat(restorations).hasValue(1);
  }

  @Test
  void restorationFailurePreservesCause() {
    assertThatThrownBy(
            () -> {
              try (PosixRawTerminal.Session session =
                  new PosixRawTerminal.Session(
                      Arena.ofConfined(),
                      new byte[] {1, 2},
                      (fileDescriptor, buffer, count) -> -1,
                      (fileDescriptor, action, attributes) -> {
                        throw new IllegalStateException("restore failed");
                      })) {
                assertThat(session).isNotNull();
              }
            })
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("restore failed");
  }
}
