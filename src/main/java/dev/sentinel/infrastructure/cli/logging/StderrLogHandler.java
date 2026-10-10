package dev.sentinel.infrastructure.cli.logging;

import java.io.PrintWriter;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** JUL handler bound to a command's stderr writer; closing a scope never closes the writer. */
public final class StderrLogHandler extends Handler {
  private final Consumer<String> sink;
  private final Runnable flush;

  public StderrLogHandler(final PrintWriter writer, final Level threshold) {
    super();
    sink = writer::print;
    flush = writer::flush;
    setFormatter(new SafeLogFormatter());
    setLevel(threshold);
  }

  @Override
  public void publish(final LogRecord record) {
    if (isLoggable(record)) {
      sink.accept(getFormatter().format(record));
      flush.run();
    }
  }

  @Override
  public void flush() {
    flush.run();
  }

  @Override
  public void close() {
    flush();
  }
}
