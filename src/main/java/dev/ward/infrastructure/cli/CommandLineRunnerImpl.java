package dev.ward.infrastructure.cli;

import dev.ward.domain.config.WardException;
import dev.ward.infrastructure.cli.logging.VerboseExecutionStrategy;
import dev.ward.infrastructure.cli.update.UpdateNoticePrinter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import picocli.CommandLine;

/**
 * Bridges application startup to Picocli and turns the command's result into the process exit code.
 */
public class CommandLineRunnerImpl {

  private final WardCommand rootCommand;
  private final DaggerCommandFactory factory;
  private final UpdateNoticePrinter updateNotice;

  /**
   * Creates the runner; {@code factory} builds subcommands, {@code rootCommand} is the {@code ward}
   * command, and {@code updateNotice} ends interactive runs with any update notice.
   */
  @Inject
  public CommandLineRunnerImpl(
      final DaggerCommandFactory factory,
      final WardCommand rootCommand,
      final UpdateNoticePrinter updateNotice) {
    this.factory = factory;
    this.rootCommand = rootCommand;
    this.updateNotice = updateNotice;
  }

  /** Runs {@code args} with UTF-8 standard output and error and returns the exit code. */
  public int run(final String... args) {
    return run(
        new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true),
        new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true),
        args);
  }

  /** Runs {@code args}, writing to {@code out} and {@code err}, and returns the exit code. */
  public int run(final PrintWriter out, final PrintWriter err, final String... args) {
    final CommandLine commandLine = createCommandLine(rootCommand, factory, out, err);
    commandLine.setExecutionStrategy(
        new VerboseExecutionStrategy(updateNotice.around(new CommandLine.RunLast())));
    final int exitCode = commandLine.execute(args);
    commandLine.getOut().flush();
    commandLine.getErr().flush();
    return exitCode;
  }

  /** Creates the configured Picocli command line for {@code root}. */
  public static CommandLine createCommandLine(
      final WardCommand root,
      final DaggerCommandFactory factory,
      final PrintWriter out,
      final PrintWriter err) {
    final CommandLine commandLine = new CommandLine(root, factory);
    commandLine.setOut(out);
    commandLine.setErr(err);
    commandLine.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    commandLine.setExecutionStrategy(executionStrategy());
    return commandLine;
  }

  /**
   * Returns the execution strategy that enables {@code --verbose} logging for the executed command.
   */
  public static CommandLine.IExecutionStrategy executionStrategy() {
    return new VerboseExecutionStrategy(new CommandLine.RunLast());
  }

  /**
   * Known failures print a clean one-line message; anything else is a bug and keeps its stack
   * trace.
   */
  static final int handle(
      final Exception e, final CommandLine commandLine, final CommandLine.ParseResult parseResult) {
    if (e instanceof WardException) {
      commandLine.getErr().println("ward: " + e.getMessage());
    } else {
      commandLine.getErr().println("ward: unexpected error");
      e.printStackTrace(commandLine.getErr());
    }
    return ExitCodes.ERROR;
  }
}
