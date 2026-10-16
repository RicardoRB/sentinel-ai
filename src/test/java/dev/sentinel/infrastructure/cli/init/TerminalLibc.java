package dev.sentinel.infrastructure.cli.init;

import java.lang.foreign.MemorySegment;

/** The libc calls the pseudo-terminal needs; {@code posixOpenpt} binds to {@code posix_openpt}. */
interface TerminalLibc {
  int posixOpenpt(int flags);

  int grantpt(int fileDescriptor);

  int unlockpt(int fileDescriptor);

  MemorySegment ptsname(int fileDescriptor);

  long read(int fileDescriptor, MemorySegment buffer, long count);

  long write(int fileDescriptor, MemorySegment buffer, long count);

  int close(int fileDescriptor);
}
