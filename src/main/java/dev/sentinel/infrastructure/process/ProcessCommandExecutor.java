package dev.sentinel.infrastructure.process;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Runs commands directly with {@link ProcessBuilder}, without a shell.
 *
 * <p>Security: the argument vector is passed to the OS as-is, so shell metacharacters ({@code ;},
 * {@code &&}, {@code |}, {@code $()}) have no special meaning. That does not make the configured
 * command safe: whoever controls {@code sentinel.toml} controls what is executed with the current
 * user's privileges. See "Security" in the README.
 */
public class ProcessCommandExecutor implements CommandExecutor {
  private static final Logger LOGGER = Logger.getLogger(ProcessCommandExecutor.class.getName());

  private static final int EXIT_COULD_NOT_START = -1;
  private static final int EXIT_INTERRUPTED = 130;

  @Inject
  public ProcessCommandExecutor() {}

  @Override
  public CommandResult execute(final List<String> command, final Path workingDirectory) {
    final long start = System.nanoTime();
    final String executable = command.isEmpty() ? "empty" : command.getFirst();
    LOGGER.log(Level.FINE, () -> "event=process-start executable=" + executable);
    try {
      final ProcessBuilder builder =
          new ProcessBuilder(resolveExecutable(command, workingDirectory))
              .directory(workingDirectory.toFile());
      final Process process = builder.start();
      process.getOutputStream().close(); // the command gets no stdin
      try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
        final Future<String> stdout = readers.submit(() -> read(process.getInputStream()));
        final Future<String> stderr = readers.submit(() -> read(process.getErrorStream()));
        try {
          final int exitCode = process.waitFor();
          final CommandResult result =
              new CommandResult(exitCode, stdout.get(), stderr.get(), since(start));
          LOGGER.log(
              Level.FINE,
              () -> "event=process-complete executable=" + executable + " status=" + exitCode);
          return result;
        } catch (InterruptedException e) {
          process.destroyForcibly();
          Thread.currentThread().interrupt();
          if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(
                Level.WARNING,
                "event=process-failure executable=" + executable + " category=interrupted");
          }
          return new CommandResult(EXIT_INTERRUPTED, "", "Interrupted", since(start));
        } catch (ExecutionException e) {
          process.destroyForcibly();
          if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(
                Level.WARNING,
                "event=process-failure executable=" + executable + " category=read-failure");
          }
          return new CommandResult(
              EXIT_COULD_NOT_START,
              "",
              "Failed to read process output: " + e.getCause(),
              since(start));
        }
      }
    } catch (IOException e) {
      if (LOGGER.isLoggable(Level.WARNING)) {
        LOGGER.log(
            Level.WARNING,
            "event=process-failure executable=" + executable + " category=start-failure");
      }
      return new CommandResult(
          EXIT_COULD_NOT_START,
          "",
          "Could not start " + command + ": " + e.getMessage(),
          since(start));
    }
  }

  /**
   * A relative executable that contains a path separator (e.g. {@code ./mvnw}) is resolved against
   * the working directory, so behaviour does not depend on the JVM's own directory.
   */
  private static List<String> resolveExecutable(
      final List<String> command, final Path workingDirectory) {
    final List<String> resolved = new ArrayList<>(command);
    final String executable = resolved.getFirst();
    if (executable.contains("/") && !Path.of(executable).isAbsolute()) {
      resolved.set(0, workingDirectory.resolve(executable).normalize().toString());
    }
    return resolved;
  }

  private static String read(final InputStream stream) throws IOException {
    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
  }

  private static Duration since(final long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }
}
