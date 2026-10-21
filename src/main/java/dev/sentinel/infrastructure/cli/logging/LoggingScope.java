package dev.sentinel.infrastructure.cli.logging;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/** Invocation-scoped configuration for the application JUL hierarchy. */
public final class LoggingScope {
  public static final String ROOT_NAME = "dev.sentinel";
  private static final ReentrantLock LOCK = new ReentrantLock(true);

  private final Logger root;
  private final Level rootLevel;
  private final Handler[] rootHandlers;
  private final boolean useParentHandlers;
  private final Map<Logger, Level> descendantLevels;
  private final Map<Logger, Handler[]> descendantHandlers;
  private final StderrLogHandler handler;

  private LoggingScope(final PrintWriter writer, final boolean verbose) {
    root = Logger.getLogger(ROOT_NAME);
    rootLevel = root.getLevel();
    rootHandlers = root.getHandlers();
    useParentHandlers = root.getUseParentHandlers();
    descendantLevels = new HashMap<>();
    descendantHandlers = new HashMap<>();
    final LogManager manager = LogManager.getLogManager();
    manager
        .getLoggerNames()
        .asIterator()
        .forEachRemaining(
            name -> {
              if (name.startsWith(ROOT_NAME + ".")) {
                final Logger logger = manager.getLogger(name);
                if (logger != null) {
                  descendantLevels.put(logger, logger.getLevel());
                  descendantHandlers.put(logger, logger.getHandlers());
                  for (final Handler existing : descendantHandlers.get(logger)) {
                    logger.removeHandler(existing);
                  }
                }
              }
            });
    final Level threshold = verbose ? Level.FINE : Level.WARNING;
    handler = new StderrLogHandler(writer, threshold);
    root.setLevel(threshold);
    root.setUseParentHandlers(false);
    for (final Handler existing : rootHandlers) {
      root.removeHandler(existing);
    }
    root.addHandler(handler);
    descendantLevels.keySet().forEach(logger -> logger.setLevel(null));
  }

  /**
   * Runs {@code action} while Sentinel's log records go to {@code writer}: warnings and above, or
   * fine-level details when {@code verbose}.
   */
  public static int run(final PrintWriter writer, final boolean verbose, final IntSupplier action) {
    LOCK.lock();
    try {
      final LoggingScope scope = new LoggingScope(writer, verbose);
      try {
        return action.getAsInt();
      } finally {
        scope.restore();
      }
    } finally {
      LOCK.unlock();
    }
  }

  private void restore() {
    root.removeHandler(handler);
    for (final Handler existing : rootHandlers) {
      root.addHandler(existing);
    }
    root.setLevel(rootLevel);
    root.setUseParentHandlers(useParentHandlers);
    descendantLevels.forEach(Logger::setLevel);
    descendantHandlers.forEach(
        (logger, handlers) -> {
          for (final Handler existing : handlers) {
            logger.addHandler(existing);
          }
        });
  }
}
