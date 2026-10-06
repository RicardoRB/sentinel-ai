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

  @Inject
  public ProcessCommandExecutor() {}

  private static final int EXIT_COULD_NOT_START = -1;
  private static final int EXIT_INTERRUPTED = 130;

  @Override
  public CommandResult execute(List<String> command, Path workingDirectory) {
    long start = System.nanoTime();
    try {
      ProcessBuilder builder =
          new ProcessBuilder(resolveExecutable(command, workingDirectory))
              .directory(workingDirectory.toFile());
      Process process = builder.start();
      process.getOutputStream().close(); // the command gets no stdin
      try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<String> stdout = readers.submit(() -> read(process.getInputStream()));
        Future<String> stderr = readers.submit(() -> read(process.getErrorStream()));
        try {
          int exitCode = process.waitFor();
          return new CommandResult(exitCode, stdout.get(), stderr.get(), since(start));
        } catch (InterruptedException e) {
          process.destroyForcibly();
          Thread.currentThread().interrupt();
          return new CommandResult(EXIT_INTERRUPTED, "", "Interrupted", since(start));
        } catch (ExecutionException e) {
          process.destroyForcibly();
          return new CommandResult(
              EXIT_COULD_NOT_START,
              "",
              "Failed to read process output: " + e.getCause(),
              since(start));
        }
      }
    } catch (IOException e) {
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
  private static List<String> resolveExecutable(List<String> command, Path workingDirectory) {
    List<String> resolved = new ArrayList<>(command);
    String executable = resolved.getFirst();
    if (executable.contains("/") && !Path.of(executable).isAbsolute()) {
      resolved.set(0, workingDirectory.resolve(executable).normalize().toString());
    }
    return resolved;
  }

  private static String read(InputStream stream) throws IOException {
    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
  }

  private static Duration since(long startNanos) {
    return Duration.ofNanos(System.nanoTime() - startNanos);
  }
}
