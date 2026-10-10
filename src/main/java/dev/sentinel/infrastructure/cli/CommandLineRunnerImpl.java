package dev.sentinel.infrastructure.cli;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.infrastructure.cli.logging.VerboseExecutionStrategy;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.inject.Inject;
import picocli.CommandLine;

/**
 * Bridges application startup to Picocli and turns the command's result into the process exit code.
 */
public class CommandLineRunnerImpl {

  private final SentinelCommand rootCommand;
  private final DaggerCommandFactory factory;

  @Inject
  public CommandLineRunnerImpl(
      final DaggerCommandFactory factory, final SentinelCommand rootCommand) {
    this.factory = factory;
    this.rootCommand = rootCommand;
  }

  public int run(final String... args) {
    return run(
        new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true),
        new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true),
        args);
  }

  public int run(final PrintWriter out, final PrintWriter err, final String... args) {
    final CommandLine commandLine = createCommandLine(rootCommand, factory, out, err);
    final int exitCode = commandLine.execute(args);
    commandLine.getOut().flush();
    commandLine.getErr().flush();
    return exitCode;
  }

  public static CommandLine createCommandLine(
      final SentinelCommand root,
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

  public static CommandLine.IExecutionStrategy executionStrategy() {
    return new VerboseExecutionStrategy(new CommandLine.RunLast());
  }

  /**
   * Known failures print a clean one-line message; anything else is a bug and keeps its stack
   * trace.
   */
  static final int handle(
      final Exception e, final CommandLine commandLine, final CommandLine.ParseResult parseResult) {
    if (e instanceof SentinelException) {
      commandLine.getErr().println("sentinel: " + e.getMessage());
    } else {
      commandLine.getErr().println("sentinel: unexpected error");
      e.printStackTrace(commandLine.getErr());
    }
    return ExitCodes.ERROR;
  }
}
