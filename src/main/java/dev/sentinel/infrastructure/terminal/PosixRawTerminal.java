package dev.sentinel.infrastructure.terminal;

import dev.sentinel.domain.init.RawTerminal;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;

/** libc-backed raw terminal adapter for macOS and Linux. */
public final class PosixRawTerminal implements RawTerminal {
  private static final int STDIN = 0;
  private final Layout layout;

  public PosixRawTerminal() {
    this(System.getProperty("os.name", ""));
  }

  PosixRawTerminal(String osName) {
    String os = osName.toLowerCase();
    Layout detected =
        os.contains("mac") || os.contains("darwin")
            ? Layout.MAC
            : os.contains("linux") ? Layout.LINUX : null;
    String architecture = System.getProperty("os.arch", "").toLowerCase();
    if (!(architecture.equals("x86_64")
        || architecture.equals("amd64")
        || architecture.equals("aarch64")
        || architecture.equals("arm64"))) detected = null;
    layout = detected;
  }

  @Override
  public Optional<RawSession> open() {
    if (layout == null) return Optional.empty();
    Arena arena = Arena.ofShared();
    try {
      Linker linker = Linker.nativeLinker();
      MethodHandle isatty =
          downcall(
              linker,
              "isatty",
              MethodType.methodType(int.class, int.class),
              FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
      MethodHandle tcgetattr =
          downcall(
              linker,
              "tcgetattr",
              MethodType.methodType(int.class, int.class, MemorySegment.class),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
      MethodHandle tcsetattr =
          downcall(
              linker,
              "tcsetattr",
              MethodType.methodType(int.class, int.class, int.class, MemorySegment.class),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT,
                  ValueLayout.JAVA_INT,
                  ValueLayout.ADDRESS));
      MethodHandle read =
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
      MemorySegment saved = arena.allocate(layout.size);
      if ((int) tcgetattr.invokeExact(STDIN, saved) != 0) {
        arena.close();
        return Optional.empty();
      }
      MemorySegment raw = arena.allocate(layout.size);
      raw.copyFrom(saved);
      long flags =
          layout.flagBytes == 4
              ? Integer.toUnsignedLong(raw.get(ValueLayout.JAVA_INT, layout.flagOffset))
              : raw.get(ValueLayout.JAVA_LONG, layout.flagOffset);
      if (layout.flagBytes == 4)
        raw.set(ValueLayout.JAVA_INT, layout.flagOffset, (int) (flags & ~layout.disableFlags));
      else raw.set(ValueLayout.JAVA_LONG, layout.flagOffset, flags & ~layout.disableFlags);
      raw.set(ValueLayout.JAVA_BYTE, layout.ccOffset + layout.vmin, (byte) 1);
      raw.set(ValueLayout.JAVA_BYTE, layout.ccOffset + layout.vtime, (byte) 0);
      if ((int) tcsetattr.invokeExact(STDIN, 0, raw) != 0) {
        arena.close();
        return Optional.empty();
      }
      return Optional.of(new Session(arena, saved, read, tcsetattr));
    } catch (Throwable failure) {
      arena.close();
      return Optional.empty();
    }
  }

  private static MethodHandle downcall(
      Linker linker, String name, MethodType type, FunctionDescriptor descriptor) {
    return linker.downcallHandle(
        Linker.nativeLinker().defaultLookup().find(name).orElseThrow(), descriptor);
  }

  private static final class Session implements RawSession {
    private final Arena arena;
    private final MemorySegment saved;
    private final MethodHandle read;
    private final MethodHandle tcsetattr;
    private final MemorySegment byteBuffer;
    private boolean closed;

    Session(Arena arena, MemorySegment saved, MethodHandle read, MethodHandle tcsetattr) {
      this.arena = arena;
      this.saved = saved;
      this.read = read;
      this.tcsetattr = tcsetattr;
      byteBuffer = arena.allocate(1);
      Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    @Override
    public int read() {
      try {
        long count = (long) read.invokeExact(STDIN, byteBuffer, 1L);
        return count == 1 ? Byte.toUnsignedInt(byteBuffer.get(ValueLayout.JAVA_BYTE, 0)) : -1;
      } catch (Throwable failure) {
        return -1;
      }
    }

    @Override
    public synchronized void close() {
      if (closed) return;
      closed = true;
      try {
        if ((int) tcsetattr.invokeExact(STDIN, 0, saved) != 0) {
          // Restoration is best-effort when the operating system rejects it.
        }
      } catch (Throwable ignored) {
        /* best effort restore */
      }
      arena.close();
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
