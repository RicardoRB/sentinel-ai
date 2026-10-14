package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.spec.GuardDecision;
import dev.sentinel.domain.spec.GuardRequest;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * The Claude Code {@code PreToolUse} approved-artifact guard. Refusals and unreadable payloads deny
 * the tool call; when Sentinel itself cannot decide, the problem goes to stderr with a non-blocking
 * exit code. It never runs quality checks or spec validation.
 */
public final class ClaudeGuardHook {
  private static final Logger LOGGER = Logger.getLogger(ClaudeGuardHook.class.getName());

  private final ApprovedArtifactGuardService service;
  private final JsonCodec codec;

  @Inject
  public ClaudeGuardHook(final ApprovedArtifactGuardService service, final JsonCodec codec) {
    this.service = service;
    this.codec = codec;
  }

  /**
   * Answers one hook event read from {@code input}, or nothing when there is no payload to read.
   *
   * @return {@code 0} after answering, or {@code 1} when Sentinel could not decide
   */
  // The hook must always answer Claude Code, even on unexpected failures.
  int run(
      final Path directory,
      final boolean readPayload,
      final InputStream input,
      final PrintWriter output,
      final PrintWriter error) {
    if (!readPayload) {
      return ExitCodes.OK;
    }
    final FutureTask<Integer> execution =
        new FutureTask<>(() -> evaluate(directory, input, output));
    execution.run();
    try {
      return execution.get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return failure(exception, error);
    } catch (ExecutionException exception) {
      return failure(exception.getCause(), error);
    }
  }

  private int evaluate(final Path directory, final InputStream input, final PrintWriter output) {
    final Optional<ClaudeHookEvent> event = ClaudeHookEvent.readComplete(input, codec);
    final Optional<GuardRequest> request =
        event.isEmpty()
            ? Optional.of(GuardRequest.unreadable(directory, "tool payload"))
            : event
                .filter(ClaudeHookEvent::isPreToolUse)
                .flatMap(found -> ClaudePreToolGuard.request(directory, found));
    LOGGER.log(Level.INFO, () -> "event=claude-guard-start guarded=" + request.isPresent());
    if (request.isEmpty()) {
      return ExitCodes.OK;
    }
    final GuardDecision decision = service.evaluate(request.get());
    if (!decision.allowed()) {
      output.println(ClaudeHookResponses.denyPreToolUse(codec, decision.reason()));
    }
    return ExitCodes.OK;
  }

  private static int failure(final Throwable failure, final PrintWriter error) {
    error.println(
        failure instanceof SentinelException
            ? "sentinel: " + failure.getMessage()
            : "sentinel: unexpected error: " + failure.getMessage());
    return ExitCodes.FAILED;
  }
}
