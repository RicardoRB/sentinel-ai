package dev.sentinel.infrastructure.terminal;

import dev.sentinel.domain.init.RawTerminal;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;

/** libc-backed raw terminal adapter for macOS and Linux. */
public final class PosixRawTerminal implements RawTerminal {
  private static final int STDIN = 0;
  private final Layout layout;

  @Inject
  public PosixRawTerminal() {
    this(System.getProperty("os.name", ""));
  }

  PosixRawTerminal(final String osName) {
    final String os = osName.toLowerCase(Locale.ROOT);
    Layout detected =
        os.contains("mac") || os.contains("darwin")
            ? Layout.MAC
            : os.contains("linux") ? Layout.LINUX : null;
    final String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (!("x86_64".equals(architecture)
        || "amd64".equals(architecture)
        || "aarch64".equals(architecture)
        || "arm64".equals(architecture))) {
      detected = null;
    }
    layout = detected;
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  public Optional<RawSession> open() {
    if (layout == null) {
      return Optional.empty();
    }
    final Arena arena = Arena.ofConfined();
    try {
      final Linker linker = Linker.nativeLinker();
      final MethodHandle isatty =
          downcall(
              linker,
              "isatty",
              MethodType.methodType(int.class, int.class),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
      final MethodHandle tcgetattr =
          downcall(
              linker,
              "tcgetattr",
              MethodType.methodType(int.class, int.class, MemorySegment.class),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      final MethodHandle tcsetattr =
          downcall(
              linker,
              "tcsetattr",
              MethodType.methodType(int.class, int.class, int.class, MemorySegment.class),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS));
      final MethodHandle read =
          downcall(
              linker,
              "read",
              MethodType.methodType(long.class, int.class, MemorySegment.class, long.class),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_LONG,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_LONG));
      if ((int) isatty.invokeExact(STDIN) == 0) {
        arena.close();
        return Optional.empty();
      }
      final MemorySegment saved = arena.allocate(layout.size);
      if ((int) tcgetattr.invokeExact(STDIN, saved) != 0) {
        arena.close();
        return Optional.empty();
      }
      final MemorySegment raw = arena.allocate(layout.size);
      raw.copyFrom(saved);
      final long flags =
          layout.flagBytes == 4
              ? Integer.toUnsignedLong(raw.get(ValueLayout.JAVA_INT, layout.flagOffset))
              : raw.get(ValueLayout.JAVA_LONG, layout.flagOffset);
      if (layout.flagBytes == 4) {
        raw.set(ValueLayout.JAVA_INT, layout.flagOffset, (int) (flags & ~layout.disableFlags));
      } else {
        raw.set(ValueLayout.JAVA_LONG, layout.flagOffset, flags & ~layout.disableFlags);
      }
      raw.set(ValueLayout.JAVA_BYTE, layout.ccOffset + layout.vmin, (byte) 1);
      raw.set(ValueLayout.JAVA_BYTE, layout.ccOffset + layout.vtime, (byte) 0);
      if ((int) tcsetattr.invokeExact(STDIN, 0, raw) != 0) {
        arena.close();
        return Optional.empty();
      }
      return Optional.of(new Session(arena, saved.toArray(ValueLayout.JAVA_BYTE), read, tcsetattr));
    } catch (Throwable failure) {
      arena.close();
      throw new IllegalStateException("Native terminal initialization failed.", failure);
    }
  }

  private static MethodHandle downcall(
      final Linker linker,
      final String name,
      final MethodType type,
      final FunctionDescriptor descriptor) {
    final MethodHandle handle =
        linker.downcallHandle(
            Linker.nativeLinker().defaultLookup().find(name).orElseThrow(), descriptor);
    if (!handle.type().equals(type)) {
      throw new IllegalArgumentException("Unexpected native signature for " + name);
    }
    return handle;
  }

  private static final class Session implements RawSession {
    private final Arena arena;
    private final byte[] savedAttributes;
    private final MethodHandle read;
    private final MethodHandle tcsetattr;
    private final MemorySegment byteBuffer;
    private boolean closed;

    Session(
        final Arena arena,
        final byte[] savedAttributes,
        final MethodHandle read,
        final MethodHandle tcsetattr) {
      this.arena = arena;
      this.savedAttributes = savedAttributes;
      this.read = read;
      this.tcsetattr = tcsetattr;
      byteBuffer = arena.allocate(1);
      Runtime.getRuntime().addShutdownHook(new Thread(this::restoreOnShutdown));
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public int read() {
      try {
        final long count = (long) read.invokeExact(STDIN, byteBuffer, 1L);
        return count == 1 ? Byte.toUnsignedInt(byteBuffer.get(ValueLayout.JAVA_BYTE, 0)) : -1;
      } catch (Throwable failure) {
        throw new IllegalStateException("Native terminal read failed.", failure);
      }
    }

    @Override
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    public synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      try {
        restoreAttributes(arena, savedAttributes, tcsetattr);
      } finally {
        arena.close();
      }
    }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    private synchronized void restoreOnShutdown() {
      if (closed) {
        return;
      }
      closed = true;
      try (Arena shutdownArena = Arena.ofConfined()) {
        restoreAttributes(shutdownArena, savedAttributes, tcsetattr);
      }
    }

    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    private static void restoreAttributes(
        final Arena targetArena, final byte[] savedAttributes, final MethodHandle tcsetattr) {
      final MemorySegment saved = targetArena.allocate(savedAttributes.length);
      saved.copyFrom(MemorySegment.ofArray(savedAttributes));
      try {
        if ((int) tcsetattr.invokeExact(STDIN, 0, saved) != 0) {
          throw new IllegalStateException("Could not restore terminal attributes.");
        }
      } catch (Throwable failure) {
        throw new IllegalStateException("Native terminal restoration failed.", failure);
      }
    }
  }

  private record Layout(
      long size,
      int flagBytes,
      long flagOffset,
      long ccOffset,
      int vmin,
      int vtime,
      long disableFlags) {
    private static final Layout LINUX =
        new Layout(60, 4, 12, 17, 6, 5, 0x00000002L | 0x00000008L | 0x00000001L);
    private static final Layout MAC =
        new Layout(72, 8, 24, 32, 16, 17, 0x00000100L | 0x00000008L | 0x00000080L);
  }
}
