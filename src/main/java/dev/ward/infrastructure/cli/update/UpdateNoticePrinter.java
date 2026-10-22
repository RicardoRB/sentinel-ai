package dev.ward.infrastructure.cli.update;

import dev.ward.application.update.UpdateNoticeService;
import dev.ward.domain.terminal.TerminalCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import picocli.CommandLine;

/**
 * Prints the update notice to stderr after an interactive command finishes; agent hooks, help,
 * version, and machine-readable output never trigger a lookup.
 */
public final class UpdateNoticePrinter {
  private static final Logger LOGGER = Logger.getLogger(UpdateNoticePrinter.class.getName());
  private static final Set<String> MACHINE_FORMATS = Set.of("json", "sarif");
  private static final String FORMAT = "--format";

  private final Supplier<Optional<String>> notice;
  private final BooleanSupplier consoleAttached;

  /** Creates a printer that asks {@code service} for a notice when a console is attached. */
  @Inject
  public UpdateNoticePrinter(
      final UpdateNoticeService service, final TerminalCapabilities terminal) {
    this(service::automaticNotice, terminal::consoleAttached);
  }

  /** Creates a printer from a {@code notice} source and a console check. */
  public UpdateNoticePrinter(
      final Supplier<Optional<String>> notice, final BooleanSupplier consoleAttached) {
    this.notice = notice;
    this.consoleAttached = consoleAttached;
  }

  /** Wraps {@code delegate} so an eligible invocation ends with the update notice. */
  public CommandLine.IExecutionStrategy around(final CommandLine.IExecutionStrategy delegate) {
    return parseResult -> {
      final int exitCode = delegate.execute(parseResult);
      if (eligible(parseResult)) {
        notice
            .get()
            .ifPresent(line -> parseResult.commandSpec().commandLine().getErr().println(line));
      }
      return exitCode;
    };
  }

  private boolean eligible(final CommandLine.ParseResult root) {
    final List<CommandLine.ParseResult> chain = chain(root);
    final String reason;
    if (chain.size() < 2 || "hook".equals(chain.get(1).commandSpec().name())) {
      reason = "command";
    } else if (chain.stream().anyMatch(UpdateNoticePrinter::helpOrVersion)) {
      reason = "help";
    } else if (chain.stream().anyMatch(UpdateNoticePrinter::machineFormat)) {
      reason = "machine-format";
    } else if (consoleAttached.getAsBoolean()) {
      return true;
    } else {
      reason = "no-console";
    }
    LOGGER.log(Level.FINE, () -> "event=update-notice status=skipped reason=" + reason);
    return false;
  }

  private static List<CommandLine.ParseResult> chain(final CommandLine.ParseResult root) {
    final List<CommandLine.ParseResult> chain = new ArrayList<>();
    for (CommandLine.ParseResult current = root; current != null; current = current.subcommand()) {
      chain.add(current);
    }
    return chain;
  }

  private static boolean helpOrVersion(final CommandLine.ParseResult result) {
    return result.isUsageHelpRequested() || result.isVersionHelpRequested();
  }

  private static boolean machineFormat(final CommandLine.ParseResult result) {
    return result.hasMatchedOption(FORMAT)
        && result.matchedOption(FORMAT).stringValues().stream()
            .anyMatch(value -> MACHINE_FORMATS.contains(value.toLowerCase(Locale.ROOT)));
  }
}
