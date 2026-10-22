package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** The controlling side of a libc pseudo-terminal pair; the child opens the device side. */
final class PseudoTerminal implements AutoCloseable {
  private static final int O_RDWR = 2;
  private static final int MAC_O_NOCTTY = 0x20000;
  private static final int LINUX_O_NOCTTY = 0x100;
  private static final long POLL_MILLIS = 50;
  private static final int READ_BUFFER_BYTES = 4096;
  private final Arena arena;
  private final TerminalLibc libc;
  private final int master;
  private final String devicePath;
  private final AtomicReference<String> output = new AtomicReference<>("");
  private final Thread reader;

  private PseudoTerminal(
      final Arena arena, final TerminalLibc libc, final int master, final String devicePath) {
    this.arena = arena;
    this.libc = libc;
    this.master = master;
    this.devicePath = devicePath;
    reader = Thread.ofPlatform().daemon().start(this::pump);
  }

  static PseudoTerminal open(final Arena arena) {
    final TerminalLibc libc = DowncallBinder.bind();
    final int noControllingTerminal =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
            ? MAC_O_NOCTTY
            : LINUX_O_NOCTTY;
    final int master = libc.posixOpenpt(O_RDWR | noControllingTerminal);
    assertThat(master).as("posix_openpt").isNotNegative();
    assertThat(libc.grantpt(master)).as("grantpt").isZero();
    assertThat(libc.unlockpt(master)).as("unlockpt").isZero();
    final MemorySegment name = libc.ptsname(master);
    assertThat(name.address()).as("ptsname").isNotZero();
    return new PseudoTerminal(arena, libc, master, name.reinterpret(Long.MAX_VALUE).getString(0));
  }

  String devicePath() {
    return devicePath;
  }

  String output() {
    return output.get();
  }

  void write(final byte[] bytes) {
    final MemorySegment buffer = arena.allocate(bytes.length);
    buffer.copyFrom(MemorySegment.ofArray(bytes));
    assertThat(libc.write(master, buffer, bytes.length))
        .as("bytes written to the terminal")
        .isEqualTo(bytes.length);
  }

  void awaitOutput(final Process process, final String marker, final Duration timeout)
      throws InterruptedException {
    final Instant deadline = Instant.now().plus(timeout);
    boolean exited = false;
    while (!output.get().contains(marker)) {
      assertThat(exited)
          .as("executable exited before \"%s\"; terminal output:%n%s", marker, output.get())
          .isFalse();
      assertThat(Instant.now())
          .as("waiting for \"%s\"; terminal output so far:%n%s", marker, output.get())
          .isBefore(deadline);
      // One more pass after exit lets the reader drain what the executable printed last.
      exited = !process.isAlive();
      Thread.sleep(POLL_MILLIS);
    }
  }

  /** Copies terminal output until the device side closes; runs on a daemon thread. */
  private void pump() {
    try (Arena buffers = Arena.ofConfined()) {
      final MemorySegment buffer = buffers.allocate(READ_BUFFER_BYTES);
      long count = libc.read(master, buffer, buffer.byteSize());
      while (count > 0) {
        output.accumulateAndGet(
            StandardCharsets.UTF_8.decode(buffer.asSlice(0, count).asByteBuffer()).toString(),
            String::concat);
        count = libc.read(master, buffer, buffer.byteSize());
      }
    }
  }

  @Override
  public void close() throws InterruptedException {
    assertThat(libc.close(master)).as("close").isZero();
    reader.join(Duration.ofSeconds(1));
  }
}
