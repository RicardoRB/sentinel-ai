package dev.sentinel.infrastructure.cli.logging;

import java.util.logging.Level;
import java.util.logging.Logger;
import picocli.CommandLine;

/** Picocli execution strategy that owns one logging scope for the complete invocation. */
public final class VerboseExecutionStrategy implements CommandLine.IExecutionStrategy {
  private static final Logger LOGGER = Logger.getLogger(VerboseExecutionStrategy.class.getName());
  private final CommandLine.IExecutionStrategy delegate;

  public VerboseExecutionStrategy(final CommandLine.IExecutionStrategy delegate) {
    this.delegate = delegate;
  }

  @Override
  public int execute(final CommandLine.ParseResult parseResult) {
    final CommandLine commandLine = parseResult.commandSpec().commandLine();
    final boolean verbose = parseResult.hasMatchedOption("--verbose");
    final boolean informational =
        !parseResult.isUsageHelpRequested() && !parseResult.isVersionHelpRequested();
    try (LoggingScope ignored = LoggingScope.open(commandLine.getErr(), verbose)) {
      if (informational) {
        LOGGER.log(Level.INFO, () -> "event=command-start command=" + commandName(parseResult));
      }
      final int result = delegate.execute(parseResult);
      if (informational) {
        LOGGER.log(Level.INFO, () -> "event=command-complete status=completed exit=" + result);
      }
      return result;
    }
  }

  private static String commandName(final CommandLine.ParseResult parseResult) {
    return SafeLogFormatter.escape(parseResult.commandSpec().qualifiedName());
  }
}
