package dev.sentinel.infrastructure.terminal;

import dev.sentinel.domain.init.RawTerminal;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;

/** libc-backed raw terminal adapter for macOS and Linux. */
public final class PosixRawTerminal implements RawTerminal {
  private static final int STDIN = 0;
  private static final int INT_FLAG_BYTES = Integer.BYTES;
  private final Layout layout;

  @Inject
  public PosixRawTerminal() {
    this(System.getProperty("os.name", ""));
  }

  PosixRawTerminal(final String osName) {
    layout = detectLayout(osName);
  }

  /** Returns the termios layout for a supported OS and 64-bit architecture, or {@code null}. */
  private static Layout detectLayout(final String osName) {
    final String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    if (!Set.of("x86_64", "amd64", "aarch64", "arm64").contains(architecture)) {
      return null;
    }
    final String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("mac") || os.contains("darwin")) {
      return Layout.MAC;
    }
    return os.contains("linux") ? Layout.LINUX : null;
  }

  @Override
  public Optional<RawSession> open() {
    if (layout == null) {
      return Optional.empty();
    }
    try (Arena arena = Arena.ofConfined()) {
      return openSession(arena);
    }
  }

  private Optional<RawSession> openSession(final Arena arena) {
    final Linker linker = Linker.nativeLinker();
    final NativeFunctions functions = NativeFunctions.create(linker);
    if (functions.isatty().call(STDIN) == 0) {
      return Optional.empty();
    }
    final MemorySegment saved = arena.allocate(layout.size);
    if (functions.tcgetattr().call(STDIN, saved) != 0) {
      return Optional.empty();
    }
    if (!enableRawMode(arena, saved, functions.tcsetattr())) {
      return Optional.empty();
    }
    try (SessionArena sessionArena = new SessionArena()) {
      final FutureTask<Session> session =
          new FutureTask<>(
              () ->
                  new Session(
                      sessionArena.arena,
                      saved.toArray(ValueLayout.JAVA_BYTE),
                      functions.read(),
                      functions.tcsetattr()));
      session.run();
      try {
        final Session value = session.get();
        sessionArena.transfer();
        return Optional.of(value);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Native terminal initialization interrupted.", interrupted);
      } catch (ExecutionException failure) {
        throw new IllegalStateException("Native terminal initialization failed.", failure);
      }
    }
  }

  private boolean enableRawMode(
      final Arena arena, final MemorySegment saved, final Tcsetattr tcsetattr) {
    final MemorySegment raw = arena.allocate(layout.size);
    raw.copyFrom(saved);
    final long flags =
        layout.flagBytes == INT_FLAG_BYTES
            ? Integer.toUnsignedLong(raw.get(ValueLayout.JAVA_INT, layout.flagOffset))
            : raw.get(ValueLayout.JAVA_LONG, layout.flagOffset);
    if (layout.flagBytes == INT_FLAG_BYTES) {
      raw.set(ValueLayout.JAVA_INT, layout.flagOffset, (int) (flags & ~layout.disableFlags));
    } else {
      raw.set(ValueLayout.JAVA_LONG, layout.flagOffset, flags & ~layout.disableFlags);
    }
    raw.set(ValueLayout.JAVA_BYTE, layout.ccOffset + layout.vmin, (byte) 1);
    raw.set(ValueLayout.JAVA_BYTE, layout.ccOffset + layout.vtime, (byte) 0);
    return tcsetattr.call(STDIN, 0, raw) == 0;
  }

  private static <T> T proxy(final Class<T> type, final MethodHandle handle) {
    return type.cast(MethodHandleProxies.asInterfaceInstance(type, handle));
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

  private record NativeFunctions(
      Isatty isatty, Tcgetattr tcgetattr, Tcsetattr tcsetattr, NativeRead read) {
    static NativeFunctions create(final Linker linker) {
      return new NativeFunctions(
          proxy(
              Isatty.class,
              downcall(
                  linker,
                  "isatty",
                  MethodType.methodType(int.class, int.class),
                  FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))),
          proxy(
              Tcgetattr.class,
              downcall(
                  linker,
                  "tcgetattr",
                  MethodType.methodType(int.class, int.class, MemorySegment.class),
                  FunctionDescriptor.of(
                      ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))),
          proxy(
              Tcsetattr.class,
              downcall(
                  linker,
                  "tcsetattr",
                  MethodType.methodType(int.class, int.class, int.class, MemorySegment.class),
                  FunctionDescriptor.of(
                      ValueLayout.JAVA_INT,
                      ValueLayout.JAVA_INT,
                      ValueLayout.JAVA_INT,
                      ValueLayout.ADDRESS))),
          proxy(
              NativeRead.class,
              downcall(
                  linker,
                  "read",
                  MethodType.methodType(long.class, int.class, MemorySegment.class, long.class),
                  FunctionDescriptor.of(
                      ValueLayout.JAVA_LONG,
                      ValueLayout.JAVA_INT,
                      ValueLayout.ADDRESS,
                      ValueLayout.JAVA_LONG))));
    }
  }

  private static final class SessionArena implements AutoCloseable {
    private final Arena arena = Arena.ofConfined();
    private boolean transferred;

    void transfer() {
      transferred = true;
    }

    @Override
    public void close() {
      if (!transferred) {
        arena.close();
      }
    }
  }

  static final class Session implements RawSession {
    private final Arena arena;
    private final byte[] savedAttributes;
    private final NativeRead read;
    private final Tcsetattr tcsetattr;
    private final MemorySegment byteBuffer;
    private final AtomicBoolean closed = new AtomicBoolean();

    Session(
        final Arena arena,
        final byte[] savedAttributes,
        final NativeRead read,
        final Tcsetattr tcsetattr) {
      this.arena = arena;
      this.savedAttributes = savedAttributes.clone();
      this.read = read;
      this.tcsetattr = tcsetattr;
      byteBuffer = arena.allocate(1);
      Runtime.getRuntime().addShutdownHook(new Thread(this::restoreOnShutdown));
    }

    @Override
    public int read() {
      final long count = read.call(STDIN, byteBuffer, 1L);
      return count == 1 ? Byte.toUnsignedInt(byteBuffer.get(ValueLayout.JAVA_BYTE, 0)) : -1;
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      try (Arena owned = arena) {
        restoreAttributes(owned, savedAttributes, tcsetattr);
      }
    }

    void restoreOnShutdown() {
      if (!closed.compareAndSet(false, true)) {
        return;
      }
      try (Arena shutdownArena = Arena.ofConfined()) {
        restoreAttributes(shutdownArena, savedAttributes, tcsetattr);
      }
    }

    private static void restoreAttributes(
        final Arena targetArena, final byte[] savedAttributes, final Tcsetattr tcsetattr) {
      final MemorySegment saved = targetArena.allocate(savedAttributes.length);
      saved.copyFrom(MemorySegment.ofArray(savedAttributes));
      final int status = tcsetattr.call(STDIN, 0, saved);
      if (status != 0) {
        throw new IllegalStateException("Could not restore terminal attributes.");
      }
    }
  }

  @FunctionalInterface
  public interface Isatty {
    int call(int fileDescriptor);
  }

  @FunctionalInterface
  public interface Tcgetattr {
    int call(int fileDescriptor, MemorySegment attributes);
  }

  @FunctionalInterface
  public interface Tcsetattr {
    int call(int fileDescriptor, int action, MemorySegment attributes);
  }

  @FunctionalInterface
  public interface NativeRead {
    long call(int fileDescriptor, MemorySegment buffer, long count);
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
